package com.devpulse.controller;

import com.devpulse.model.CompileEvent;
import com.devpulse.repository.CompileEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/sandbox")
@CrossOrigin(origins = "*")
public class SandboxController {

    private final CompileEventRepository compileEventRepository;

    public SandboxController(CompileEventRepository compileEventRepository) {
        this.compileEventRepository = compileEventRepository;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runCode(@RequestBody Map<String, String> request) {
        String sessionToken = request.get("sessionToken");
        String language = request.getOrDefault("language", "java").toLowerCase();
        String code = request.get("code");

        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code content cannot be empty"));
        }

        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            sessionToken = "anonymous";
        }

        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();

        if (language.equals("java")) {
            executeJava(code, sessionToken, response);
        } else if (language.equals("javascript") || language.equals("js")) {
            executeJavaScript(code, sessionToken, response);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language: " + language));
        }

        long executionTime = System.currentTimeMillis() - startTime;
        response.put("executionTimeMs", executionTime);

        return ResponseEntity.ok(response);
    }

    private void executeJava(String code, String sessionToken, Map<String, Object> response) {
        // Find public class name or default to Main
        String className = "Main";
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            className = matcher.group(1);
        }

        // Create a unique temporary directory to avoid concurrent compilation conflicts
        String runId = UUID.randomUUID().toString().substring(0, 8);
        File tempDir = new File("temp/" + runId);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File sourceFile = new File(tempDir, className + ".java");
        try {
            // Write code to file
            Files.writeString(sourceFile.toPath(), code);

            // Compile the Java file
            ProcessBuilder compileBuilder = new ProcessBuilder("javac", className + ".java");
            compileBuilder.directory(tempDir);
            Process compileProcess = compileBuilder.start();
            
            String compileErrors = readStream(compileProcess.getErrorStream());
            boolean compileSuccess = compileProcess.waitFor(5, TimeUnit.SECONDS) && compileProcess.exitValue() == 0;

            if (!compileSuccess) {
                response.put("success", false);
                response.put("output", "");
                response.put("errors", compileErrors.isEmpty() ? "Compilation timed out or failed." : compileErrors);
                
                // Log compile failure
                compileEventRepository.save(new CompileEvent(
                        sessionToken,
                        LocalDateTime.now(),
                        "java",
                        false,
                        compileErrors,
                        "CompilationError"
                ));
                return;
            }

            // Run the compiled class
            ProcessBuilder runBuilder = new ProcessBuilder("java", "-cp", ".", className);
            runBuilder.directory(tempDir);
            Process runProcess = runBuilder.start();

            // Read output stream in background (to prevent hang on filled buffer)
            StreamReader stdoutReader = new StreamReader(runProcess.getInputStream());
            StreamReader stderrReader = new StreamReader(runProcess.getErrorStream());
            stdoutReader.start();
            stderrReader.start();

            boolean finished = runProcess.waitFor(4, TimeUnit.SECONDS);

            if (!finished) {
                runProcess.destroyForcibly();
                stdoutReader.join(1000);
                stderrReader.join(1000);
                
                response.put("success", false);
                response.put("output", stdoutReader.getOutput());
                response.put("errors", "Execution timed out (Limit: 4 seconds).");
                
                compileEventRepository.save(new CompileEvent(
                        sessionToken,
                        LocalDateTime.now(),
                        "java",
                        false,
                        "Execution timed out.",
                        "TimeoutError"
                ));
                return;
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);

            String stdout = stdoutReader.getOutput();
            String stderr = stderrReader.getOutput();
            boolean runSuccess = runProcess.exitValue() == 0;

            response.put("success", runSuccess);
            response.put("output", stdout);
            response.put("errors", stderr);

            // Log compile success
            compileEventRepository.save(new CompileEvent(
                    sessionToken,
                    LocalDateTime.now(),
                    "java",
                    runSuccess,
                    stderr.isEmpty() ? null : stderr,
                    runSuccess ? null : "RuntimeError"
            ));

        } catch (Exception e) {
            response.put("success", false);
            response.put("output", "");
            response.put("errors", "System error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up files and directories
            deleteDirectory(tempDir);
        }
    }

    private void executeJavaScript(String code, String sessionToken, Map<String, Object> response) {
        // Simple fallback execution of JavaScript using node if available, or basic evaluation simulation
        // Create a unique temporary directory
        String runId = UUID.randomUUID().toString().substring(0, 8);
        File tempDir = new File("temp/" + runId);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File sourceFile = new File(tempDir, "script.js");
        try {
            Files.writeString(sourceFile.toPath(), code);

            // Attempt to run node
            ProcessBuilder runBuilder = new ProcessBuilder("node", "script.js");
            runBuilder.directory(tempDir);
            Process runProcess = runBuilder.start();

            StreamReader stdoutReader = new StreamReader(runProcess.getInputStream());
            StreamReader stderrReader = new StreamReader(runProcess.getErrorStream());
            stdoutReader.start();
            stderrReader.start();

            boolean finished = runProcess.waitFor(3, TimeUnit.SECONDS);

            if (!finished) {
                runProcess.destroyForcibly();
                response.put("success", false);
                response.put("output", "");
                response.put("errors", "Execution timed out (Limit: 3 seconds).");
                return;
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);

            String stdout = stdoutReader.getOutput();
            String stderr = stderrReader.getOutput();
            boolean runSuccess = runProcess.exitValue() == 0;

            response.put("success", runSuccess);
            response.put("output", stdout);
            response.put("errors", stderr);

            compileEventRepository.save(new CompileEvent(
                    sessionToken,
                    LocalDateTime.now(),
                    "javascript",
                    runSuccess,
                    stderr.isEmpty() ? null : stderr,
                    runSuccess ? null : "RuntimeError"
            ));

        } catch (IOException e) {
            // Node is likely not installed, provide a fallback message
            response.put("success", false);
            response.put("output", "");
            response.put("errors", "JavaScript execution requires Node.js to be installed on the host machine. Please install Node.js or run Java code instead!");
        } catch (Exception e) {
            response.put("success", false);
            response.put("output", "");
            response.put("errors", "System error: " + e.getMessage());
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private String readStream(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        dir.delete();
    }

    // Static helper class to read streams concurrently to avoid process buffer deadlock
    private static class StreamReader extends Thread {
        private final InputStream is;
        private final StringBuilder output = new StringBuilder();

        public StreamReader(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                // Ignore
            }
        }

        public String getOutput() {
            return output.toString();
        }
    }
}
