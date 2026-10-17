package com.aaron.voicenotes.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Extractive baseline: returns transcript sentences unchanged and never invents content. */
@Service
public class TranscriptAnalysis {
    private static final Pattern DECISION = Pattern.compile("(?i)\\b(decided|decision|agreed)\\b");
    private static final Pattern ACTION = Pattern.compile("(?i)\\b(action item|to-do|todo|will do|will send|will prepare|will follow up)\\b");

    public Analysis analyze(String transcript) {
        if (transcript == null || transcript.isBlank()) return new Analysis("", List.of(), List.of(), "EMPTY_TRANSCRIPT");
        List<String> sentences = Arrays.stream(transcript.split("(?<=[.!?])\\s+|\\R"))
                .map(String::trim).filter(s -> !s.isBlank()).limit(200).toList();
        String summary = String.join(" ", sentences.stream().limit(3).toList());
        List<String> decisions = sentences.stream().filter(s -> DECISION.matcher(s).find()).limit(10).toList();
        List<String> actions = sentences.stream().filter(s -> ACTION.matcher(s).find()).limit(10).toList();
        return new Analysis(summary, decisions, actions, "EXTRACTIVE_RULES");
    }

    public record Analysis(String summary, List<String> decisions, List<String> actions, String method) { }
}
