// DevPulse Dashboard Client Application Logic

document.addEventListener('DOMContentLoaded', () => {
    // 1. Core State
    let sessionToken = localStorage.getItem('devpulse_session_token');
    if (!sessionToken) {
        sessionToken = 'sess_' + Math.random().toString(36).substring(2, 14);
        localStorage.setItem('devpulse_session_token', sessionToken);
    }
    document.getElementById('display-session-id').textContent = sessionToken;

    let ws = null;
    let editor = null;
    let telemetryChart = null;
    let intervalKeystrokes = 0;
    let activeLanguage = 'java';
    let emaWpm = 0.0;
    const intervalSeconds = 5;
    let telemetryIntervalId = null;

    // Default code templates
    const templates = {
        java: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, DevPulse!");
        
        // Try making compile errors or loops to test telemetry
        for (int i = 1; i <= 5; i++) {
            System.out.println("Loop index: " + i);
        }
    }
}`,
        javascript: `// Write JavaScript node-compatible code here
console.log("Hello, JavaScript sandbox!");
const sum = (a, b) => a + b;
console.log("Sum: " + sum(12, 30));`
    };

    // 2. Tab Navigation System
    const navItems = document.querySelectorAll('.nav-item');
    const tabPanels = document.querySelectorAll('.tab-panel');
    const tabTitle = document.getElementById('tab-title');

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            const targetTab = item.getAttribute('data-tab');
            
            // Toggle active classes in sidebar
            navItems.forEach(nav => nav.classList.remove('active'));
            item.classList.add('active');

            // Toggle active classes in panels
            tabPanels.forEach(panel => panel.classList.remove('active'));
            document.getElementById(`tab-${targetTab}`).classList.add('active');

            // Update Header Title
            if (targetTab === 'dashboard') tabTitle.textContent = "Developer Telemetry Dashboard";
            else if (targetTab === 'sandbox') tabTitle.textContent = "Compile & Execution Sandbox";
            else if (targetTab === 'copilot') tabTitle.textContent = "AI Developer Co-Pilot";
            else if (targetTab === 'settings') tabTitle.textContent = "System Configurations";

            // If entering dashboard, refresh chart layout
            if (targetTab === 'dashboard' && telemetryChart) {
                setTimeout(() => telemetryChart.resize(), 50);
                refreshChartData();
            }
        });
    });

    // 3. Initialize Monaco Editor
    require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.39.0/min/vs' } });
    require(['vs/editor/editor.main'], () => {
        editor = monaco.editor.create(document.getElementById('editor-container'), {
            value: templates.java,
            language: 'java',
            theme: 'vs-dark',
            automaticLayout: true,
            fontSize: 14,
            fontFamily: "'JetBrains Mono', Consolas, monospace",
            minimap: { enabled: false },
            lineHeight: 22,
            scrollbar: {
                vertical: 'visible',
                horizontal: 'visible'
            }
        });

        // Event listener to count keystrokes
        editor.onDidChangeModelContent(() => {
            intervalKeystrokes++;
        });
    });

    // Language selector change
    const selectLang = document.getElementById('select-lang');
    selectLang.addEventListener('change', (e) => {
        const lang = e.target.value;
        activeLanguage = lang;
        if (editor) {
            monaco.editor.setModelLanguage(editor.getModel(), lang === 'java' ? 'java' : 'javascript');
            editor.setValue(templates[lang]);
        }
    });

    // 4. WebSocket Telemetry Stream Connection
    function connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws-telemetry`;
        
        ws = new WebSocket(wsUrl);
        const indicator = document.getElementById('connection-status');
        const dot = document.querySelector('.status-indicator .dot');

        ws.onopen = () => {
            indicator.textContent = "Connected (WS)";
            dot.className = "dot pulse";
            // Start recording telemetry periodic ticks
            startTelemetryInterval();
        };

        ws.onclose = () => {
            indicator.textContent = "Offline (Reconnecting)";
            dot.className = "dot";
            stopTelemetryInterval();
            setTimeout(connectWebSocket, 3000);
        };

        ws.onerror = () => {
            ws.close();
        };

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'telemetry_update') {
                    updateDashboardCards(data);
                }
            } catch (err) {
                console.error("WS parse error", err);
            }
        };
    }

    // Start sending telemetry data every 5 seconds
    function startTelemetryInterval() {
        if (telemetryIntervalId) clearInterval(telemetryIntervalId);
        
        telemetryIntervalId = setInterval(() => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                // Calculate typing WPM for the 5s interval: (keystrokes / 5 characters per word) / (5 seconds / 60 seconds)
                // WPM = (keystrokes / 5) * 12 = keystrokes * 2.4
                const currentWpm = (intervalKeystrokes / 5.0) * 12.0;
                
                // Smooth with Exponential Moving Average (EMA)
                if (intervalKeystrokes > 0) {
                    emaWpm = emaWpm === 0.0 ? currentWpm : (emaWpm * 0.7) + (currentWpm * 0.3);
                } else {
                    emaWpm = emaWpm * 0.5; // decays quickly when idle
                }

                const payload = {
                    sessionToken: sessionToken,
                    keystrokeCount: intervalKeystrokes,
                    typingSpeedWpm: parseFloat(emaWpm.toFixed(1)),
                    activeFileExtension: activeLanguage === 'java' ? 'java' : 'js',
                    intervalSeconds: intervalSeconds
                };

                ws.send(JSON.stringify(payload));
                
                // Reset interval count
                intervalKeystrokes = 0;
            }
        }, intervalSeconds * 1000);
    }

    function stopTelemetryInterval() {
        if (telemetryIntervalId) {
            clearInterval(telemetryIntervalId);
            telemetryIntervalId = null;
        }
    }

    // 5. Update UI Dashboard Cards
    function updateDashboardCards(data) {
        // Active time formatting
        const totalSeconds = data.activeTimeSeconds || 0;
        const mins = Math.floor(totalSeconds / 60);
        const secs = totalSeconds % 60;
        document.getElementById('val-active-time').textContent = `${mins}m ${secs.toString().padStart(2, '0')}s`;

        // Typing Speed & Keystrokes
        document.getElementById('val-typing-speed').innerHTML = `${Math.round(data.typingSpeedWpm)} <span class="unit">WPM</span>`;
        document.getElementById('val-total-keystrokes').textContent = `Total keys: ${data.totalKeystrokes}`;

        // Focus Level Card
        const focusVal = document.getElementById('val-focus-level');
        const focusFooter = document.getElementById('val-focus-footer');
        focusVal.textContent = data.focusLevel;

        if (data.fatigueAlert) {
            focusVal.className = "card-value text-danger";
            focusFooter.innerHTML = `<span class="badge" style="background-color: rgba(255,23,68,0.15); color: var(--accent-red); border: 1px solid rgba(255,23,68,0.3)">TAKE A BREAK</span>`;
        } else if (data.focusLevel === "Flow State") {
            focusVal.className = "card-value text-success";
            focusFooter.innerHTML = `<span class="badge focus-badge">Flow Active</span>`;
        } else if (data.focusLevel === "Idle") {
            focusVal.className = "card-value";
            focusVal.style.color = "var(--text-secondary)";
            focusFooter.innerHTML = `<span class="badge" style="background-color: rgba(255,255,255,0.05); color: var(--text-secondary); border: 1px solid var(--border-color)">Idle</span>`;
        } else {
            focusVal.className = "card-value";
            focusVal.style.color = "var(--text-primary)";
            focusFooter.innerHTML = `<span class="badge badge-success">Focusing</span>`;
        }
    }

    // 6. Chart.js Timeline Implementation
    function initChart() {
        const ctx = document.getElementById('telemetryChart').getContext('2d');
        
        telemetryChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    {
                        label: 'Typing Speed (WPM)',
                        data: [],
                        borderColor: '#00d2ff',
                        backgroundColor: 'rgba(0, 210, 255, 0.1)',
                        borderWidth: 2,
                        tension: 0.4,
                        fill: true,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Keystrokes / Interval',
                        data: [],
                        borderColor: '#7f00ff',
                        backgroundColor: 'transparent',
                        borderWidth: 1.5,
                        tension: 0.3,
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        labels: {
                            color: '#8b949e',
                            font: { family: "'Inter', sans-serif" }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(255,255,255,0.03)' },
                        ticks: { color: '#8b949e' }
                    },
                    y: {
                        position: 'left',
                        grid: { color: 'rgba(255,255,255,0.03)' },
                        ticks: { color: '#8b949e' },
                        title: { display: true, text: 'Words Per Minute', color: '#8b949e' }
                    },
                    y1: {
                        position: 'right',
                        grid: { drawOnChartArea: false },
                        ticks: { color: '#8b949e' },
                        title: { display: true, text: 'Keystroke count', color: '#8b949e' }
                    }
                }
            }
        });
    }

    function refreshChartData() {
        fetch(`/api/telemetry/session/${sessionToken}/chart`)
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(data => {
                if (telemetryChart) {
                    telemetryChart.data.labels = data.timestamps;
                    telemetryChart.data.datasets[0].data = data.wpm;
                    telemetryChart.data.datasets[1].data = data.keystrokes;
                    telemetryChart.update();
                }
            })
            .catch(err => console.error("Chart fetch failed", err));
    }

    // Refresh summary metrics from REST API
    function refreshSummaryStats() {
        fetch(`/api/telemetry/session/${sessionToken}/summary`)
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(data => {
                document.getElementById('val-compile-rate').textContent = `${Math.round(data.compilationSuccessRate)}%`;
                document.getElementById('val-compile-stats').textContent = `Successful: ${data.successfulCompiles} / ${data.totalCompiles} runs`;
            })
            .catch(err => console.error("Summary fetch failed", err));
    }

    // 7. REST API: Start Session
    function startNewSession() {
        sessionToken = 'sess_' + Math.random().toString(36).substring(2, 14);
        localStorage.setItem('devpulse_session_token', sessionToken);
        document.getElementById('display-session-id').textContent = sessionToken;
        
        fetch(`/api/telemetry/session/${sessionToken}/start`, { method: 'POST' })
            .then(res => res.json())
            .then(sessionData => {
                // Clear cards to defaults
                document.getElementById('val-active-time').textContent = "0m 00s";
                document.getElementById('val-typing-speed').innerHTML = `0 <span class="unit">WPM</span>`;
                document.getElementById('val-total-keystrokes').textContent = `Total keys: 0`;
                document.getElementById('val-compile-rate').textContent = "0%";
                document.getElementById('val-compile-stats').textContent = "Successful: 0 / 0 runs";
                emaWpm = 0.0;
                intervalKeystrokes = 0;
                
                // Clear outputs
                document.getElementById('standup-output').innerHTML = '<p class="placeholder-text">Click the button to process today\'s metrics and generate daily standup documentation.</p>';
                document.getElementById('analysis-output').innerHTML = '<p class="placeholder-text">Click the button to scan your Monaco Editor sandbox code for potential code smells and design advice.</p>';

                // Reconnect Websocket with new token
                if (ws) ws.close();
                else connectWebSocket();

                // Clear chart
                if (telemetryChart) {
                    telemetryChart.data.labels = [];
                    telemetryChart.data.datasets[0].data = [];
                    telemetryChart.data.datasets[1].data = [];
                    telemetryChart.update();
                }
            });
    }

    document.getElementById('btn-start-session').addEventListener('click', startNewSession);

    // 8. REST API: Sandbox Code Run
    const btnRunCode = document.getElementById('btn-run-code');
    const consoleStdout = document.getElementById('console-stdout');
    const consoleStatusBadge = document.getElementById('console-status-badge');
    const runTimeDisplay = document.getElementById('run-time-display');

    btnRunCode.addEventListener('click', () => {
        if (!editor) return;

        const code = editor.getValue();
        btnRunCode.disabled = true;
        btnRunCode.textContent = "Compiling... ⚙️";
        consoleStatusBadge.textContent = "Compiling";
        consoleStatusBadge.className = "badge badge-success"; // yellow-glowing style

        const payload = {
            sessionToken: sessionToken,
            language: activeLanguage,
            code: code
        };

        fetch('/api/sandbox/run', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(res => res.json())
        .then(data => {
            runTimeDisplay.textContent = `Execution Time: ${data.executionTimeMs} ms`;
            
            if (data.success) {
                consoleStdout.textContent = data.output;
                if (data.errors && data.errors.trim() !== "") {
                    consoleStdout.textContent += "\n--- STDERR ---\n" + data.errors;
                }
                consoleStatusBadge.textContent = "Success";
                consoleStatusBadge.className = "badge focus-badge";
            } else {
                consoleStdout.textContent = data.errors || "Execution failed with no error diagnostics.";
                if (data.output && data.output.trim() !== "") {
                    consoleStdout.textContent = data.output + "\n--- ERRORS ---\n" + consoleStdout.textContent;
                }
                consoleStatusBadge.textContent = "Failed";
                consoleStatusBadge.className = "badge";
                consoleStatusBadge.style.backgroundColor = "rgba(255,23,68,0.15)";
                consoleStatusBadge.style.color = "var(--accent-red)";
                consoleStatusBadge.style.border = "1px solid rgba(255,23,68,0.3)";
            }
            
            // Refresh dashboard compiles stat card
            refreshSummaryStats();
        })
        .catch(err => {
            consoleStdout.textContent = "Network Error connecting to Sandbox compiler: " + err.message;
            consoleStatusBadge.textContent = "Err Connection";
            consoleStatusBadge.className = "badge";
            consoleStatusBadge.style.backgroundColor = "var(--accent-red)";
        })
        .finally(() => {
            btnRunCode.disabled = false;
            btnRunCode.textContent = "Run & Compile ⚡";
        });
    });

    // 9. REST API: AI Operations
    const btnGenStandup = document.getElementById('btn-gen-standup');
    const standupOutput = document.getElementById('standup-output');
    
    btnGenStandup.addEventListener('click', () => {
        btnGenStandup.disabled = true;
        btnGenStandup.textContent = "Analyzing... 🧠";
        
        const apiKey = localStorage.getItem('openai_api_key') || "";
        const headers = {};
        if (apiKey) {
            headers['Authorization'] = `Bearer ${apiKey}`;
        }

        fetch(`/api/ai/standup?sessionToken=${sessionToken}`, { headers })
            .then(res => res.json())
            .then(data => {
                standupOutput.innerHTML = marked.parse(data.markdown);
                
                // Add source badge
                const badge = document.createElement('div');
                badge.style.marginTop = '16px';
                badge.style.fontSize = '11px';
                badge.style.color = 'var(--text-secondary)';
                badge.innerHTML = `Engine source: <code style="color:var(--accent-blue)">${data.source}</code>`;
                standupOutput.appendChild(badge);
            })
            .catch(err => {
                standupOutput.innerHTML = `<p class="text-danger">Failed to generate daily standup summary. Verify backend server logs. Error: ${err.message}</p>`;
            })
            .finally(() => {
                btnGenStandup.disabled = false;
                btnGenStandup.textContent = "Generate Standup 🗓️";
            });
    });

    const btnAnalyzeCode = document.getElementById('btn-analyze-code');
    const analysisOutput = document.getElementById('analysis-output');

    btnAnalyzeCode.addEventListener('click', () => {
        if (!editor) return;

        btnAnalyzeCode.disabled = true;
        btnAnalyzeCode.textContent = "Analyzing Code... ⚙️";
        
        const code = editor.getValue();
        const apiKey = localStorage.getItem('openai_api_key') || "";
        const headers = { 'Content-Type': 'application/json' };
        if (apiKey) {
            headers['Authorization'] = `Bearer ${apiKey}`;
        }

        const payload = {
            code: code,
            language: activeLanguage
        };

        fetch('/api/ai/refactor', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        })
        .then(res => res.json())
        .then(data => {
            analysisOutput.innerHTML = marked.parse(data.markdown);
            
            const badge = document.createElement('div');
            badge.style.marginTop = '16px';
            badge.style.fontSize = '11px';
            badge.style.color = 'var(--text-secondary)';
            badge.innerHTML = `Engine source: <code style="color:var(--accent-blue)">${data.source}</code>`;
            analysisOutput.appendChild(badge);
        })
        .catch(err => {
            analysisOutput.innerHTML = `<p class="text-danger">Failed to execute static code analysis. Error: ${err.message}</p>`;
        })
        .finally(() => {
            btnAnalyzeCode.disabled = false;
            btnAnalyzeCode.textContent = "Analyze Code Quality 🔍";
        });
    });

    // 10. Settings Configuration
    const btnSaveSettings = document.getElementById('btn-save-settings');
    const openaiApiKeyInput = document.getElementById('openai-api-key');
    const activeEngineBadge = document.getElementById('active-engine-badge');

    // Load existing Settings
    const savedKey = localStorage.getItem('openai_api_key') || "";
    openaiApiKeyInput.value = savedKey;
    if (savedKey) {
        activeEngineBadge.textContent = "OpenAI GPT-4 Enabled";
        activeEngineBadge.className = "badge focus-badge";
    }

    btnSaveSettings.addEventListener('click', () => {
        const key = openaiApiKeyInput.value.trim();
        if (key) {
            localStorage.setItem('openai_api_key', key);
            activeEngineBadge.textContent = "OpenAI GPT-4 Enabled";
            activeEngineBadge.className = "badge focus-badge";
            alert("Settings saved successfully! OpenAI API Key is cached locally in browser.");
        } else {
            localStorage.removeItem('openai_api_key');
            activeEngineBadge.textContent = "Local Heuristics Engine";
            activeEngineBadge.className = "badge badge-success";
            alert("Settings updated: OpenAI API key cleared. Running in default local heuristics mode.");
        }
    });

    // Start Execution initialization
    connectWebSocket();
    initChart();
    
    // Initial data load
    fetch(`/api/telemetry/session/${sessionToken}/start`, { method: 'POST' })
        .then(() => {
            refreshChartData();
            refreshSummaryStats();
        });

    // Periodically refresh chart (every 10 seconds)
    setInterval(() => {
        const activeTab = document.querySelector('.nav-item.active').getAttribute('data-tab');
        if (activeTab === 'dashboard') {
            refreshChartData();
        }
    }, 10000);
});
