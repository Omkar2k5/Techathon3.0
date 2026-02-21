import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
    ArrowLeft, Send, Loader2, Bot, User, Cpu, Wifi,
    WifiOff, Trash2, BookOpen, GraduationCap, ShieldAlert
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import { sendChatMessage, type ChatMessage } from "@/lib/api";

// ── Mode metadata ─────────────────────────────────────────────────────────────
const MODE_META: Record<string, { label: string; badge: string; color: string; desc: string }> = {
    TEACHER: {
        label: "Teacher Mode",
        badge: "Full Access",
        color: "text-emerald-600",
        desc: "Unrestricted — code generation, evaluation, and full explanations enabled.",
    },
    GLOBAL: {
        label: "General Assistance",
        badge: "Full Access",
        color: "text-blue-600",
        desc: "Concept explanations, theory, syntax help, and general subject queries.",
    },
    LAB_ACTIVE: {
        label: "Lab Active",
        badge: "Limited Mode",
        color: "text-amber-600",
        desc: "Lab session is running. Concept and error help only — no full solutions.",
    },
    POST_LAB: {
        label: "Post-Lab Review",
        badge: "Full Access",
        color: "text-purple-600",
        desc: "Lab ended. Full solution walkthroughs and optimisation discussions enabled.",
    },
};

// ── Welcome messages ──────────────────────────────────────────────────────────
const WELCOME: Record<string, string> = {
    teacher:
        "Welcome, Professor! I'm your EduNet Academic Assistant.\n\nI can help you with:\n• Generating assignment questions\n• Explaining concepts for teaching\n• Reviewing code solutions\n• Creating evaluation rubrics\n\nWhat would you like to work on?",
    student:
        "Hi! I'm your EduNet Academic Assistant.\n\nI can help you with:\n• Concept explanations and theory\n• Understanding compiler / runtime errors\n• Syntax questions\n• Debugging your logic step by step\n\nDuring active lab sessions, I'll guide your thinking without giving away the answer. Ask me anything!",
};

// ── Main Component ────────────────────────────────────────────────────────────
const ChatPage = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState("");
    const [loading, setLoading] = useState(false);
    const [mode, setMode] = useState("GLOBAL");
    const [llmOnline, setLlmOnline] = useState<boolean | null>(null);
    const bottomRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);

    const isTeacher = user?.role === "staff";
    const dashboardPath = isTeacher ? "/staff/dashboard" : "/student/dashboard";

    useEffect(() => {
        if (user) {
            const role = isTeacher ? "teacher" : "student";
            setMessages([{ role: "assistant", content: WELCOME[role] }]);
            setMode(isTeacher ? "TEACHER" : "GLOBAL");
        }
    }, [user]);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, loading]);

    const send = async () => {
        if (!input.trim() || !user || loading) return;
        const userMsg: ChatMessage = { role: "user", content: input.trim() };
        setMessages((m) => [...m, userMsg]);
        setInput("");
        setLoading(true);

        try {
            const res = await sendChatMessage({
                message: userMsg.content,
                user_id: user.id,
                user_role: isTeacher ? "teacher" : "student",
            });
            setMode(res.mode);
            setLlmOnline(true);
            setMessages((m) => [...m, { role: "assistant", content: res.reply }]);
        } catch {
            setLlmOnline(false);
            setMessages((m) => [
                ...m,
                {
                    role: "assistant",
                    content:
                        "The assistant is temporarily unavailable. Please try again in a moment.\n\nIf the issue persists, the LLM server may be offline or unreachable.",
                },
            ]);
        } finally {
            setLoading(false);
            inputRef.current?.focus();
        }
    };

    const clearChat = () => {
        const role = isTeacher ? "teacher" : "student";
        setMessages([{ role: "assistant", content: WELCOME[role] }]);
        setMode(isTeacher ? "TEACHER" : "GLOBAL");
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            send();
        }
    };

    const meta = MODE_META[mode] ?? MODE_META.GLOBAL;

    return (
        <div className="min-h-screen bg-background flex flex-col">
            {/* ── Header ──────────────────────────────────────────────────────── */}
            <header className="border-b border-border bg-card sticky top-0 z-10">
                <div className="container flex items-center gap-4 h-16 px-6">
                    <Button variant="ghost" size="icon" onClick={() => navigate(dashboardPath)}>
                        <ArrowLeft className="w-4 h-4" />
                    </Button>
                    <div className="flex items-center gap-3 flex-1 min-w-0">
                        <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center shrink-0">
                            <Bot className="w-4 h-4 text-primary-foreground" />
                        </div>
                        <div className="min-w-0">
                            <h1 className="text-sm font-bold tracking-tight">EduNet Assistant</h1>
                            <p className="text-xs text-muted-foreground truncate">
                                {isTeacher ? "Staff Portal" : "Student Portal"} · {user?.name}
                            </p>
                        </div>
                    </div>

                    {/* Mode badge */}
                    <div className={`flex items-center gap-1.5 text-xs font-medium ${meta.color}`}>
                        {mode === "LAB_ACTIVE" && <ShieldAlert className="w-3.5 h-3.5" />}
                        <span>{meta.label}</span>
                        <Badge variant="outline" className={`text-[10px] px-1.5 py-0 ${meta.color} border-current`}>
                            {meta.badge}
                        </Badge>
                    </div>

                    {/* LLM status */}
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                        {llmOnline === true ? (
                            <><Wifi className="w-3.5 h-3.5 text-emerald-500" /><span className="hidden sm:inline">Online</span></>
                        ) : llmOnline === false ? (
                            <><WifiOff className="w-3.5 h-3.5 text-destructive" /><span className="hidden sm:inline">Offline</span></>
                        ) : (
                            <><Cpu className="w-3.5 h-3.5 animate-pulse" /><span className="hidden sm:inline">Ready</span></>
                        )}
                    </div>

                    <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground" onClick={clearChat} title="Clear chat">
                        <Trash2 className="w-4 h-4" />
                    </Button>

                    <Button variant="ghost" size="sm" onClick={() => { logout(); navigate("/"); }}>
                        {isTeacher
                            ? <><BookOpen className="w-4 h-4 mr-2" />Sign Out</>
                            : <><GraduationCap className="w-4 h-4 mr-2" />Sign Out</>
                        }
                    </Button>
                </div>
            </header>

            {/* ── Body: Sidebar + Chat ─────────────────────────────────────────── */}
            <div className="flex flex-1 overflow-hidden container px-0 sm:px-6 py-0 sm:py-6 gap-4">

                {/* Sidebar: Model Info */}
                <aside className="hidden lg:flex flex-col gap-4 w-72 shrink-0 pt-0 sm:pt-2">
                    {/* Model card */}
                    <Card className="border-border">
                        <CardContent className="p-5 space-y-4">
                            <div className="flex items-center gap-2">
                                <Cpu className="w-4 h-4 text-primary" />
                                <p className="text-sm font-semibold">Model Info</p>
                            </div>
                            <div className="space-y-3 text-xs text-muted-foreground">
                                <div className="flex justify-between">
                                    <span>Student model</span>
                                    <span className="font-mono text-foreground text-right">phi3-fast:latest</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Teacher model</span>
                                    <span className="font-mono text-foreground text-right">deepseek-coder-v2:16b</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Inference</span>
                                    <span className="font-mono text-foreground">Ollama (local)</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Fallback</span>
                                    <span className="font-mono text-foreground">Peer LLM (LAN)</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Context</span>
                                    <span className="font-mono text-foreground">Role-aware</span>
                                </div>
                                <div className="flex justify-between">
                                    <span>Privacy</span>
                                    <span className="font-mono text-emerald-600">Offline — no leak</span>
                                </div>
                            </div>
                        </CardContent>
                    </Card>

                    {/* Mode description */}
                    <Card className="border-border">
                        <CardContent className="p-5 space-y-3">
                            <p className={`text-sm font-semibold ${meta.color}`}>{meta.label}</p>
                            <p className="text-xs text-muted-foreground leading-relaxed">{meta.desc}</p>

                            {mode === "LAB_ACTIVE" && (
                                <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-xs text-amber-800">
                                    ⚠️ Full solutions blocked during active lab
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    {/* Tips */}
                    <Card className="border-border">
                        <CardContent className="p-5 space-y-2">
                            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Tips</p>
                            <ul className="text-xs text-muted-foreground space-y-1.5">
                                <li>• Press <kbd className="font-mono bg-secondary px-1 rounded">Enter</kbd> to send</li>
                                <li>• <kbd className="font-mono bg-secondary px-1 rounded">Shift+Enter</kbd> for a new line</li>
                                <li>• Paste error messages directly</li>
                                <li>• Ask "why" not just "how"</li>
                            </ul>
                        </CardContent>
                    </Card>
                </aside>

                {/* Chat area */}
                <div className="flex flex-col flex-1 min-h-0 bg-card sm:rounded-xl border border-border overflow-hidden">
                    {/* Messages */}
                    <div className="flex-1 overflow-y-auto px-4 py-5 space-y-5">
                        {messages.map((m, i) => (
                            <div key={i} className={`flex gap-3 ${m.role === "user" ? "justify-end" : "justify-start"}`}>
                                {m.role === "assistant" && (
                                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 mt-0.5">
                                        <Bot className="h-4 w-4 text-primary" />
                                    </div>
                                )}
                                <div
                                    className={`max-w-[78%] rounded-2xl px-4 py-3 text-sm whitespace-pre-wrap leading-relaxed ${m.role === "user"
                                        ? "bg-primary text-primary-foreground rounded-tr-sm"
                                        : "bg-secondary text-secondary-foreground rounded-tl-sm"
                                        }`}
                                >
                                    {m.content}
                                </div>
                                {m.role === "user" && (
                                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary mt-0.5">
                                        <User className="h-4 w-4 text-primary-foreground" />
                                    </div>
                                )}
                            </div>
                        ))}

                        {loading && (
                            <div className="flex gap-3 justify-start">
                                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10">
                                    <Bot className="h-4 w-4 text-primary" />
                                </div>
                                <div className="bg-secondary rounded-2xl rounded-tl-sm px-4 py-3">
                                    <div className="flex gap-1.5 items-center">
                                        <div className="w-1.5 h-1.5 bg-muted-foreground/50 rounded-full animate-bounce" style={{ animationDelay: "0ms" }} />
                                        <div className="w-1.5 h-1.5 bg-muted-foreground/50 rounded-full animate-bounce" style={{ animationDelay: "150ms" }} />
                                        <div className="w-1.5 h-1.5 bg-muted-foreground/50 rounded-full animate-bounce" style={{ animationDelay: "300ms" }} />
                                    </div>
                                </div>
                            </div>
                        )}
                        <div ref={bottomRef} />
                    </div>

                    {/* Input bar */}
                    <div className="border-t border-border bg-card px-4 py-3">
                        {mode === "LAB_ACTIVE" && (
                            <div className="flex items-center gap-2 text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-1.5 mb-3">
                                <ShieldAlert className="w-3.5 h-3.5 shrink-0" />
                                Lab Active — concept and error help only
                            </div>
                        )}
                        <div className="flex gap-2 items-end">
                            <textarea
                                ref={inputRef}
                                rows={1}
                                className="flex-1 resize-none rounded-xl border border-border bg-background px-3 py-2.5 text-sm outline-none focus:border-foreground/40 transition-colors min-h-[42px] max-h-32"
                                placeholder="Ask a question… (Enter to send)"
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={handleKeyDown}
                                style={{ height: "auto" }}
                                onInput={(e) => {
                                    const t = e.currentTarget;
                                    t.style.height = "auto";
                                    t.style.height = Math.min(t.scrollHeight, 128) + "px";
                                }}
                            />
                            <Button
                                size="icon"
                                onClick={send}
                                disabled={loading || !input.trim()}
                                className="h-10 w-10 rounded-xl shrink-0"
                            >
                                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                            </Button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ChatPage;
