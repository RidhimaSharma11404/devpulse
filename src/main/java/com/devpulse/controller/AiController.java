package com.devpulse.controller;

import com.devpulse.model.CompileEvent;
import com.devpulse.model.TelemetryEvent;
import com.devpulse.model.TelemetrySession;
import com.devpulse.repository.CompileEventRepository;
import com.devpulse.repository.TelemetryEventRepository;
import com.devpulse.repository.TelemetrySessionRepository;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private final TelemetrySessionRepository sessionRepository;
    private final TelemetryEventRepository eventRepository;
    private final CompileEventRepository compileEventRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public AiController(TelemetrySessionRepository sessionRepository,
                        TelemetryEventRepository eventRepository,
                        CompileEventRepository compileEventRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.compileEventRepository = compileEventRepository;
    }

    @GetMapping("/standup")
    public ResponseEntity<?> getDailyStandup(@RequestParam String sessionToken, 
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        Optional<TelemetrySession> sessionOpt = sessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid session token"));
        }

        TelemetrySession session = sessionOpt.get();
        List<TelemetryEvent> events = eventRepository.findBySessionToken(sessionToken);
        List<CompileEvent> compiles = compileEventRepository.findBySessionToken(sessionToken);

        // Extract session metrics
        long activeSeconds = session.getActiveTimeSeconds();
        int totalKeystrokes = session.getTotalKeystrokes();
        long totalCompiles = compiles.size();
        long successfulCompiles = compiles.stream().filter(CompileEvent::getSuccess).count();
        long failedCompiles = totalCompiles - successfulCompiles;

        // Calculate average WPM
        double avgWpm = events.stream()
                .mapToDouble(TelemetryEvent::getTypingSpeedWpm)
                .average()
                .orElse(0.0);

        // Find primary language
        Map<String, Long> langCounts = events.stream()
                .collect(Collectors.groupingBy(TelemetryEvent::getActiveFileExtension, Collectors.counting()));
        String primaryLang = langCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("java");

        // Identify common compile errors
        Map<String, Long> errorCounts = compiles.stream()
                .filter(c -> !c.getSuccess() && c.getErrorType() != null)
                .collect(Collectors.groupingBy(CompileEvent::getErrorType, Collectors.counting()));
        String commonErrors = errorCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        // Check if user provided OpenAI Key, if so use LLM, else fall back to local rule engine
        String apiKey = getApiKey(authHeader);
        if (apiKey != null) {
            try {
                String prompt = String.format(
                        "You are an AI standup assistant. Generate a professional daily standup update based on these developer metrics:\n" +
                        "- Active Coding Time: %d minutes\n" +
                        "- Total Keystrokes: %d\n" +
                        "- Avg Typing Speed: %.1f WPM\n" +
                        "- Primary Language: %s\n" +
                        "- Total Compilations: %d (Success: %d, Failed: %d)\n" +
                        "- Common Error Type: %s\n" +
                        "Format the output in clean Markdown with sections: 'What I did', 'Impediments', and 'Next Steps'. Keep it concise and developer-focused.",
                        activeSeconds / 60, totalKeystrokes, avgWpm, primaryLang, totalCompiles, successfulCompiles, failedCompiles, commonErrors
                );
                String response = callOpenAi(apiKey, prompt);
                return ResponseEntity.ok(Map.of("markdown", response, "source", "OpenAI GPT-4"));
            } catch (Exception e) {
                // Fallback to local on API failure
                System.err.println("OpenAI Call failed: " + e.getMessage());
            }
        }

        // Local Rule Engine Fallback (Super fast, works offline!)
        String standupMarkdown = generateLocalStandup(activeSeconds, totalKeystrokes, avgWpm, primaryLang, totalCompiles, successfulCompiles, failedCompiles, commonErrors);
        return ResponseEntity.ok(Map.of("markdown", standupMarkdown, "source", "DevPulse Local Analytics Engine"));
    }

    @PostMapping("/refactor")
    public ResponseEntity<?> analyzeCode(@RequestBody Map<String, String> payload,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String code = payload.get("code");
        String language = payload.getOrDefault("language", "java");

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code is empty"));
        }

        String apiKey = getApiKey(authHeader);
        if (apiKey != null) {
            try {
                String prompt = String.format(
                        "Analyze the following %s code for complexity, cleanliness, and security vulnerabilities. " +
                        "Provide a brief code review in Markdown format. Rate the code (A, B, C, D, or F) and provide specific refactoring recommendations.\n\n" +
                        "Code:\n```%s\n%s\n```",
                        language, language, code
                );
                String response = callOpenAi(apiKey, prompt);
                return ResponseEntity.ok(Map.of("markdown", response, "source", "OpenAI GPT-4"));
            } catch (Exception e) {
                System.err.println("OpenAI Call failed: " + e.getMessage());
            }
        }

        // Local static analysis heuristics
        String codeAnalysisMarkdown = generateLocalCodeAnalysis(code, language);
        return ResponseEntity.ok(Map.of("markdown", codeAnalysisMarkdown, "source", "DevPulse Static Analyzer"));
    }

    private String getApiKey(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String key = authHeader.substring(7).trim();
            if (!key.isEmpty() && !key.equals("null") && !key.equals("undefined")) {
                return key;
            }
        }
        // Check system environment
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return null;
    }

    private String callOpenAi(String apiKey, String prompt) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("messages", List.of(message));
        body.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                return (String) msg.get("content");
            }
        }
        throw new RuntimeException("Unexpected response format from OpenAI API");
    }

    private String generateLocalStandup(long activeSeconds, int totalKeystrokes, double avgWpm, String primaryLang, 
                                        long totalCompiles, long successfulCompiles, long failedCompiles, String commonErrors) {
        long minutes = activeSeconds / 60;
        double successRate = totalCompiles == 0 ? 100.0 : ((double) successfulCompiles / totalCompiles) * 100.0;
        
        StringBuilder sb = new StringBuilder();
        sb.append("### 🗓️ Daily Standup Report\n");
        sb.append("> Generated by **DevPulse Local Analytics Engine** at ").append(LocalDateTime.now().toString().substring(0, 16).replace("T", " ")).append("\n\n");
        
        sb.append("#### 🚀 What I Did Today\n");
        sb.append(String.format("- Spent **%d minutes** in active coding in the **%s** sandbox.\n", minutes, primaryLang.toUpperCase()));
        sb.append(String.format("- Typed a total of **%d characters** (Avg Speed: **%.1f WPM**).\n", totalKeystrokes, avgWpm));
        sb.append(String.format("- Conducted **%d compilations/executions** (Success Rate: **%.1f%%**).\n", totalCompiles, successRate));
        
        sb.append("\n#### 🚧 Impediments & Challenges\n");
        if (failedCompiles > 0) {
            sb.append(String.format("- Encountered **%d runtime/compilation failures**.\n", failedCompiles));
            sb.append(String.format("- Primary blocker: resolved issues related to **%s**.\n", commonErrors));
        } else {
            sb.append("- **No major impediments.** Smooth coding workflow with 100% compilation success rate!\n");
        }

        sb.append("\n#### 🎯 Next Steps\n");
        if (successRate < 70) {
            sb.append("- Focus on fixing compiler diagnostics and syntax structure.\n");
            sb.append("- Write unit tests to prevent recurring runtime errors.\n");
        } else if (minutes > 45) {
            sb.append("- Refactor the code written during this session to reduce complexity.\n");
            sb.append("- Review performance bottlenecks and optimize database indexes.\n");
        } else {
            sb.append("- Expand core module features and implement remaining user stories.\n");
        }
        
        return sb.toString();
    }

    private String generateLocalCodeAnalysis(String code, String language) {
        int lines = code.split("\r\n|\r|\n").length;
        
        // Dynamic complexity counts
        List<String> warnings = new ArrayList<>();
        int score = 100;
        
        // Heuristic 1: Length of code
        if (lines > 150) {
            warnings.add("- **Code Length**: File exceeds 150 lines. Consider breaking this down into smaller classes or modules.");
            score -= 10;
        }

        // Heuristic 2: Long methods
        Pattern methodPattern = Pattern.compile("(public|private|protected)\\s+\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{");
        Matcher methodMatcher = methodPattern.matcher(code);
        int methodCount = 0;
        while (methodMatcher.find()) {
            methodCount++;
        }
        
        // Heuristic 3: Check for nested ifs/loops
        int maxNesting = 0;
        int currentNesting = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') {
                currentNesting++;
                if (currentNesting > maxNesting) {
                    maxNesting = currentNesting;
                }
            } else if (c == '}') {
                currentNesting = Math.max(0, currentNesting - 1);
            }
        }
        if (maxNesting > 4) {
            warnings.add(String.format("- **Deep Nesting**: Detected block nesting depth of **%d**. High nesting increases cognitive load. Consider refactoring with guard clauses or helper methods.", maxNesting));
            score -= 15;
        }

        // Heuristic 4: Check for system outputs (bad practice in production)
        if (code.contains("System.out.print") || code.contains("console.log")) {
            warnings.add("- **Console Logging**: Using stdout printing instead of a formal Logger (e.g. SLF4J/Logback).");
            score -= 5;
        }

        // Heuristic 5: Empty catch blocks
        if (code.contains("catch") && (code.contains("catch (Exception e) {}") || code.contains("catch(Exception e) {}") || code.contains("catch (Exception e) {\n}"))) {
            warnings.add("- **Empty Catch Block**: Catching exceptions without logging or rethrowing. This can swallow bugs silently.");
            score -= 15;
        }

        // Determine grade
        String grade = "A";
        if (score < 60) grade = "F";
        else if (score < 70) grade = "D";
        else if (score < 80) grade = "C";
        else if (score < 90) grade = "B";

        StringBuilder sb = new StringBuilder();
        sb.append("### 🔍 DevPulse Static Code Analysis Report\n");
        sb.append("> Analyzed **").append(language.toUpperCase()).append("** code (").append(lines).append(" lines)\n\n");
        
        sb.append("#### 📊 Code Health Grade: **").append(grade).append("** (Score: ").append(score).append("/100)\n\n");
        
        sb.append("#### 🛠️ Refactoring Recommendations\n");
        if (warnings.isEmpty()) {
            sb.append("- 🎉 **Excellent work!** No code smells detected. The code conforms to clean coding guidelines.\n");
        } else {
            for (String warning : warnings) {
                sb.append(warning).append("\n");
            }
        }

        sb.append("\n#### 💡 Suggestions for SDE Interviews\n");
        sb.append("1. **Single Responsibility Principle (SRP)**: Ensure each class has exactly one reason to change.\n");
        sb.append("2. **Exception Handling**: Avoid catching generic `Exception`. Catch specific subclass exceptions (`IOException`, `IllegalArgumentException`) to show robust design.\n");
        sb.append("3. **Dependency Injection**: If writing Spring code, explain constructor injection to your interviewer instead of `@Autowired` field injection.\n");

        return sb.toString();
    }
}
