package com.example.youtube;

import com.opencsv.CSVReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CsvParserUtil {
    public static List<String> extractChannelIds(String filePath) throws Exception {
        List<String> channelIds = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            boolean headerSkipped = false;
            while ((line = reader.readNext()) != null) {
                if (!headerSkipped) { 
                    headerSkipped = true; 
                    continue; 
                }
                if (line.length > 0 && line[0] != null && !line[0].trim().isEmpty()) {
                    channelIds.add(line[0].trim());
                }
            }
        }
        return channelIds;
    }
}
