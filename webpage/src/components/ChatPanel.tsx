import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { MessageCircle, X, Send, Loader2, Bot, User } from "lucide-react";
import { sendChatMessage, type ChatMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";

interface ChatPanelProps {
    assignmentId?: string;
}

const MODE_META: Record<string, { label: string; color: string; description: string }> = {
    LAB_ACTIVE: {
        label: "Lab Active — Limited Mode",
        color: "bg-amber-500",
        description: "Concept help only. No full solutions during active lab.",
    },
    TEACHER: {
        label: "Teacher Mode — Full Access",
        color: "bg-emerald-600",
        description: "Full assistance, code generation, and evaluation tools.",
    },
    GLOBAL: {
        label: "Full Assistance Mode",
        color: "bg-blue-600",
        description: "Concept explanations, theory, and syntax help.",
    },
    POST_LAB: {
        label: "Post-Lab — Full Access",
        color: "bg-purple-600",
        description: "Lab ended. Full solution discussion enabled.",
    },
};

export default function ChatPanel({ assignmentId }: ChatPanelProps) {
    const { user } = useAuth();
    const [open, setOpen] = useState(false);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState("");
    const [loading, setLoading] = useState(false);
    const [mode, setMode] = useState<string>("GLOBAL");
    const bottomRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, loading]);

    const send = async () => {
        if (!input.trim() || !user) return;
        const userMsg: ChatMessage = { role: "user", content: input.trim() };
        setMessages((m) => [...m, userMsg]);
        setInput("");
        setLoading(true);

        try {
            const res = await sendChatMessage({
                message: userMsg.content,
                user_id: user.id,
                user_role: user.role,
                assignment_id: assignmentId,
            });
            setMode(res.mode);
            setMessages((m) => [...m, { role: "assistant", content: res.reply }]);
        } catch {
            setMessages((m) => [
                ...m,
                {
                    role: "assistant",
                    content: "The assistant is temporarily unavailable. Please try again.",
                },
            ]);
        } finally {
            setLoading(false);
        }
    };

    const meta = MODE_META[mode] ?? MODE_META.GLOBAL;

    return (
        <>
            {/* Toggle Button */}
            <button
                onClick={() => setOpen((o) => !o)}
                className="fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-zinc-900 text-white shadow-xl hover:bg-zinc-700 transition-colors"
                aria-label="Toggle EduNet Assistant"
            >
                {open ? <X className="h-6 w-6" /> : <MessageCircle className="h-6 w-6" />}
            </button>

            {/* Panel */}
            {open && (
                <div className="fixed bottom-24 right-6 z-50 flex w-[360px] max-w-[95vw] flex-col rounded-2xl border border-zinc-200 bg-white shadow-2xl overflow-hidden">
                    {/* Header */}
                    <div className={`${meta.color} px-4 py-3 text-white`}>
                        <div className="flex items-center gap-2">
                            <Bot className="h-5 w-5 shrink-0" />
                            <div>
                                <p className="text-sm font-semibold leading-none">EduNet Assistant</p>
                                <p className="text-xs opacity-80 mt-0.5">{meta.label}</p>
                            </div>
                        </div>
                        {messages.length === 0 && (
                            <p className="mt-2 text-xs opacity-75 border-t border-white/20 pt-2">
                                {meta.description}
                            </p>
                        )}
                    </div>

                    {/* Messages */}
                    <div className="flex-1 overflow-y-auto px-3 py-3 space-y-3 max-h-[380px] min-h-[200px] bg-zinc-50">
                        {messages.length === 0 && (
                            <p className="text-center text-xs text-zinc-400 mt-6">
                                Ask me anything about your coursework.
                            </p>
                        )}
                        {messages.map((m, i) => (
                            <div
                                key={i}
                                className={`flex gap-2 ${m.role === "user" ? "justify-end" : "justify-start"}`}
                            >
                                {m.role === "assistant" && (
                                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-zinc-200 mt-1">
                                        <Bot className="h-4 w-4 text-zinc-600" />
                                    </div>
                                )}
                                <div
                                    className={`max-w-[80%] rounded-2xl px-3 py-2 text-sm whitespace-pre-wrap ${m.role === "user"
                                            ? "bg-zinc-900 text-white rounded-tr-sm"
                                            : "bg-white text-zinc-800 border border-zinc-200 rounded-tl-sm"
                                        }`}
                                >
                                    {m.content}
                                </div>
                                {m.role === "user" && (
                                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-zinc-900 mt-1">
                                        <User className="h-4 w-4 text-white" />
                                    </div>
                                )}
                            </div>
                        ))}
                        {loading && (
                            <div className="flex gap-2 justify-start">
                                <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-zinc-200">
                                    <Bot className="h-4 w-4 text-zinc-600" />
                                </div>
                                <div className="bg-white border border-zinc-200 rounded-2xl rounded-tl-sm px-3 py-2">
                                    <Loader2 className="h-4 w-4 animate-spin text-zinc-400" />
                                </div>
                            </div>
                        )}
                        <div ref={bottomRef} />
                    </div>

                    {/* Input */}
                    <div className="flex gap-2 border-t border-zinc-100 bg-white px-3 py-3">
                        <input
                            className="flex-1 rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm outline-none focus:border-zinc-400 focus:ring-0"
                            placeholder="Ask a question…"
                            value={input}
                            onChange={(e) => setInput(e.target.value)}
                            onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && send()}
                        />
                        <Button
                            size="sm"
                            onClick={send}
                            disabled={loading || !input.trim()}
                            className="rounded-xl bg-zinc-900 hover:bg-zinc-700 px-3"
                        >
                            <Send className="h-4 w-4" />
                        </Button>
                    </div>
                </div>
            )}
        </>
    );
}
