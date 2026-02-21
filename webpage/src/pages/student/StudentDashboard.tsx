import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  GraduationCap, LogOut, Wifi, Clock, RefreshCw, BookOpen, Bot,
  PlusCircle, Loader2, Users
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import { getActiveAssignments, getStudentSubjects, joinSubject, getPeerCount, type ActiveAssignment, type Subject } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

function Countdown({ deadline }: { deadline: string }) {
  const [label, setLabel] = useState("");
  const [pct, setPct] = useState(100);
  useEffect(() => {
    const end = new Date(deadline).getTime();
    const total = Math.max(1, end - Date.now());
    const update = () => {
      const diff = end - Date.now();
      if (diff <= 0) { setLabel("Expired"); setPct(0); return; }
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      setLabel(h > 0 ? `${h}h ${m}m ${s}s` : `${m}m ${s}s`);
      setPct(Math.max(0, (diff / total) * 100));
    };
    update();
    const t = setInterval(update, 1000);
    return () => clearInterval(t);
  }, [deadline]);
  const barColor = pct > 40 ? "bg-primary" : pct > 15 ? "bg-orange-500" : "bg-red-500";
  return (
    <div className="space-y-1">
      <div className="flex items-center gap-1 text-xs font-semibold text-foreground">
        <Clock className="w-3 h-3" />{label} left
      </div>
      <div className="w-full h-1 bg-secondary rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-[width] duration-1000 ${barColor}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

const StudentDashboard = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { toast } = useToast();
  const [assignments, setAssignments] = useState<ActiveAssignment[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [peerCount, setPeerCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<"labs" | "subjects">("labs");
  const [joinOpen, setJoinOpen] = useState(false);
  const [joinCode, setJoinCode] = useState("");
  const [joining, setJoining] = useState(false);

  const doFetch = async () => {
    if (!user) return;
    const [aData, sData, pData] = await Promise.all([
      getActiveAssignments(user.id),
      getStudentSubjects(user.id),
      getPeerCount(),
    ]);
    setAssignments(aData);
    setSubjects(sData);
    setPeerCount(pData.count);
    setLoading(false);
  };

  useEffect(() => {
    doFetch();
    const interval = setInterval(doFetch, 10000);
    return () => clearInterval(interval);
  }, [user]);

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!joinCode.trim()) return;
    setJoining(true);
    try {
      const res = await joinSubject(user!.id, joinCode.trim().toUpperCase());
      toast({ title: `Joined ${res.subject_name}!`, description: `Code: ${res.subject_code}` });
      setJoinOpen(false);
      setJoinCode("");
      doFetch();
    } catch (err: any) {
      toast({ title: "Failed to join", description: err.message, variant: "destructive" });
    } finally { setJoining(false); }
  };

  const handleLogout = () => { logout(); navigate("/"); };
  const liveCount = assignments.filter(a => a.is_active).length;

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card sticky top-0 z-10">
        <div className="container flex items-center justify-between h-16 px-6">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
              <GraduationCap className="w-4 h-4 text-primary-foreground" />
            </div>
            <div>
              <h1 className="text-sm font-bold tracking-tight">EduNet</h1>
              <p className="text-xs text-muted-foreground">
                Student Portal · {user?.name}
                {user?.roll_no && <> · <span className="font-mono">{user.roll_no}</span></>}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {peerCount > 0 && (
              <span className="flex items-center gap-1 text-xs text-emerald-600 font-medium px-2 py-1 bg-emerald-50 dark:bg-emerald-900/20 rounded-full">
                <Wifi className="w-3 h-3 animate-pulse" />{peerCount} peer{peerCount > 1 ? "s" : ""} on LAN
              </span>
            )}
            <Button variant="ghost" size="sm" className="gap-2" onClick={() => navigate("/chat")}>
              <Bot className="w-4 h-4" />Assistant
            </Button>
            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={doFetch}>
              <RefreshCw className="w-4 h-4" />
            </Button>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="w-4 h-4 mr-2" />Sign Out
            </Button>
          </div>
        </div>
      </header>

      <main className="container px-6 py-8">
        {/* Live banner */}
        {liveCount > 0 && (
          <div className="mb-6 p-4 bg-primary text-primary-foreground rounded-xl flex items-center gap-3">
            <Wifi className="w-5 h-5 animate-pulse shrink-0" />
            <div>
              <p className="text-sm font-semibold">{liveCount} Active Lab Session{liveCount > 1 ? "s" : ""}</p>
              <p className="text-xs opacity-80">Click a card below to open and submit your solution</p>
            </div>
          </div>
        )}

        {/* Tabs */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-1 p-1 bg-secondary rounded-lg">
            <button onClick={() => setTab("labs")}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === "labs" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>
              Active Labs {liveCount > 0 && <span className="ml-1 w-5 h-5 bg-primary text-primary-foreground rounded-full text-xs inline-flex items-center justify-center">{liveCount}</span>}
            </button>
            <button onClick={() => setTab("subjects")}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${tab === "subjects" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`}>
              My Subjects
            </button>
          </div>
          <Button size="sm" className="gap-2 bg-black text-white hover:bg-black/80" onClick={() => setJoinOpen(true)}>
            <PlusCircle className="w-4 h-4" />Join Subject
          </Button>
        </div>

        {/* Join dialog */}
        <Dialog open={joinOpen} onOpenChange={setJoinOpen}>
          <DialogContent className="sm:max-w-sm">
            <DialogHeader><DialogTitle>Join a Subject</DialogTitle></DialogHeader>
            <form onSubmit={handleJoin} className="space-y-4 mt-2">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">Enter the subject code given by your teacher.</p>
                <Input placeholder="e.g. CS301" value={joinCode}
                  onChange={e => setJoinCode(e.target.value.toUpperCase())} className="font-mono uppercase" />
              </div>
              <Button type="submit" className="w-full" disabled={joining || !joinCode.trim()}>
                {joining ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" />Joining...</> : "Join Subject"}
              </Button>
            </form>
          </DialogContent>
        </Dialog>

        {/* Content */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : tab === "labs" ? (
          assignments.length === 0 ? (
            <div className="text-center py-20 text-muted-foreground">
              <BookOpen className="w-12 h-12 mx-auto mb-4 opacity-20" />
              <p className="text-sm font-medium">No active lab sessions</p>
              <p className="text-xs mt-2 max-w-xs mx-auto">When your instructor starts a lab, it will appear here. Auto-checks every 10s.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {assignments.map((assignment) => (
                <Card key={assignment.id}
                  className="border-border ring-1 ring-foreground/10 cursor-pointer hover:ring-foreground/30 hover:shadow-sm transition-all"
                  onClick={() => navigate(`/student/assignment/${assignment.id}`)}>
                  <CardContent className="p-6">
                    <div className="flex items-start justify-between mb-3">
                      <span className="font-mono text-xs px-2 py-1 bg-secondary rounded">{assignment.subject_code}</span>
                      <Badge className="bg-primary text-primary-foreground gap-1 text-xs">
                        <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse" />LIVE
                      </Badge>
                    </div>
                    <h3 className="font-semibold">{assignment.subject_name}</h3>
                    <p className="text-sm text-muted-foreground font-medium">{assignment.assignment_name}</p>
                    {assignment.teacher_name && (
                      <p className="text-xs text-muted-foreground mt-1">by {assignment.teacher_name}</p>
                    )}
                    <div className="mt-4"><Countdown deadline={assignment.deadline} /></div>
                    <div className="mt-3 flex items-center gap-3 text-xs text-muted-foreground">
                      <span className="font-mono">{assignment.allowed_file_types.join(", ")}</span>
                    </div>
                    <p className="text-xs mt-3 font-medium text-primary">Tap to open & submit →</p>
                  </CardContent>
                </Card>
              ))}
            </div>
          )
        ) : (
          subjects.length === 0 ? (
            <div className="text-center py-20 text-muted-foreground">
              <Users className="w-12 h-12 mx-auto mb-4 opacity-20" />
              <p className="text-sm font-medium">No subjects joined yet</p>
              <p className="text-xs mt-2">Click "Join Subject" and enter your subject code.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {subjects.map((s) => (
                <Card key={s.id} className="border-border cursor-pointer hover:ring-1 hover:ring-foreground/20 hover:shadow-sm transition-all" onClick={() => navigate(`/student/subject/${s.id}`)}>
                  <CardContent className="p-6">
                    <span className="font-mono text-xs px-2 py-1 bg-secondary rounded">{s.subject_code}</span>
                    <h3 className="font-semibold mt-3">{s.subject_name}</h3>
                    {(s as any).teacher_name && (
                      <p className="text-xs text-muted-foreground mt-1">Teacher: {(s as any).teacher_name}</p>
                    )}
                    <p className="text-xs text-primary font-medium mt-3">Tap to view your submissions →</p>
                  </CardContent>
                </Card>
              ))}
            </div>
          )

        )}
      </main>
    </div>
  );
};

export default StudentDashboard;
