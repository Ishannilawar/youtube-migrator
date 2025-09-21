package com.example.youtube;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class YouTubeResubscribe {
    public static void main(String[] args) throws Exception {
        String credentialsFile = "credentials.json";
        String applicationName = "YT-Subscriptions-Migrator";
        String csvFile = "subscriptions.csv";
        String completedFile = "completed.txt";  // progress tracker

        // Authorize new YouTube account
        YouTube youtube = YouTubeAuth.getService(credentialsFile, applicationName);

        // Load all channel IDs from CSV
        List<String> channelIds = CsvParserUtil.extractChannelIds(csvFile);

        // Load already completed IDs
        Set<String> completed = new HashSet<>();
        if (Files.exists(Paths.get(completedFile))) {
            completed.addAll(Files.readAllLines(Paths.get(completedFile)));
        }

        // Prepare writer for new progress
        BufferedWriter writer = new BufferedWriter(new FileWriter(completedFile, true));

        // Counters
        int newSubs = 0;
        int duplicates = 0;
        int failed = 0;
        boolean quotaHit = false;

        // Show starting summary
        System.out.println("===== Starting Run =====");
        System.out.println("Total channels: " + channelIds.size() +
                           ", Completed: " + completed.size() +
                           ", Remaining: " + (channelIds.size() - completed.size()));

        for (String channelId : channelIds) {
            // Skip already processed IDs (don’t waste quota)
            if (completed.contains(channelId)) {
                continue;
            }

            SubscriptionSnippet snippet = new SubscriptionSnippet();
            ResourceId resourceId = new ResourceId();
            resourceId.setKind("youtube#channel");
            resourceId.setChannelId(channelId);
            snippet.setResourceId(resourceId);

            Subscription subscription = new Subscription();
            subscription.setSnippet(snippet);

            try {
                youtube.subscriptions().insert("snippet", subscription).execute();
                System.out.println("Subscribed to: " + channelId);

                // Mark as done
                writer.write(channelId);
                writer.newLine();
                writer.flush();

                newSubs++;
            } catch (Exception e) {
                String msg = e.getMessage();
                System.err.println("Failed to subscribe to " + channelId + ": " + msg);

                // If quota is hit, stop immediately (don’t mark this one as completed)
                if (msg != null && msg.contains("quotaExceeded")) {
                    System.err.println("YouTube quota reached. Stopping now.");
                    quotaHit = true;
                    break;
                }

                // If it's a duplicate, mark as completed so we skip it next run
                if (msg != null && msg.contains("subscriptionDuplicate")) {
                    writer.write(channelId);
                    writer.newLine();
                    writer.flush();
                    System.out.println("Already subscribed (marked as done): " + channelId);
                    duplicates++;
                } else {
                    // Other errors → count as failed, do not mark as completed
                    System.err.println("Temporary/other error for " + channelId + " — will retry next run.");
                    failed++;
                }
            }

            Thread.sleep(500); // avoid hammering API
        }

        writer.close();

        // Print summary
        System.out.println("\n===== Run Summary =====");
        System.out.println("New: " + newSubs + ", Already Subscribed: " + duplicates + ", Failed: " + failed);
        int doneTotal = completed.size() + newSubs + duplicates; // failed not included
        int remaining = channelIds.size() - doneTotal;
        System.out.println("Completed so far: " + doneTotal + " / " + channelIds.size());
        System.out.println("Remaining: " + remaining);

        if (quotaHit) {
            System.out.println("\n⚠️ Stopped early due to YouTube quota limit. Please rerun tomorrow to continue.");
        } else {
            System.out.println("\n✅ Finished all subscriptions!");
        }
    }
}
