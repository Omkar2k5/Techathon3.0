import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { GraduationCap, LogOut, Wifi, Clock, RefreshCw, BookOpen } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { getActiveAssignments, type ActiveAssignment } from "@/lib/api";

function Countdown({ deadline }: { deadline: string }) {
  const [label, setLabel] = useState("");
  const [pct, setPct] = useState(100);

  useEffect(() => {
    const mountTime = Date.now();
    const end = new Date(deadline).getTime();
    const total = Math.max(1, end - mountTime);

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
        <Clock className="w-3 h-3" />
        {label} left
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
  const [assignments, setAssignments] = useState<ActiveAssignment[]>([]);
  const [loading, setLoading] = useState(true);

  const doFetch = async () => {
    if (!user) return;
    const data = await getActiveAssignments(user.id);
    setAssignments(data);
    setLoading(false);
  };

  useEffect(() => {
    doFetch();
    const interval = setInterval(doFetch, 10000);
    return () => clearInterval(interval);
  }, [user]);

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
            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={doFetch}>
              <RefreshCw className="w-4 h-4" />
            </Button>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="w-4 h-4 mr-2" />
              Sign Out
            </Button>
          </div>
        </div>
      </header>

      <main className="container px-6 py-8">
        {/* Network banner */}
        {liveCount > 0 && (
          <div className="mb-6 p-4 bg-primary text-primary-foreground rounded-xl flex items-center gap-3">
            <Wifi className="w-5 h-5 animate-pulse shrink-0" />
            <div>
              <p className="text-sm font-semibold">{liveCount} Active Lab Session{liveCount > 1 ? "s" : ""} on Network</p>
              <p className="text-xs opacity-80">Click a card below to open and submit your solution</p>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">Active Labs</h2>
            <p className="text-muted-foreground text-sm">
              {loading ? "Loading..." : liveCount > 0 ? `${liveCount} session(s) running · auto-refreshes every 10s` : "No active sessions right now · checking every 10s"}
            </p>
          </div>
        </div>

        {!loading && assignments.length === 0 ? (
          <div className="text-center py-20 text-muted-foreground">
            <BookOpen className="w-12 h-12 mx-auto mb-4 opacity-20" />
            <p className="text-sm font-medium">No active lab sessions</p>
            <p className="text-xs mt-2 max-w-xs mx-auto">
              When your instructor starts a lab, it will appear here automatically.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {assignments.map((assignment) => (
              <Card
                key={assignment.id}
                className="border-border ring-1 ring-foreground/10 cursor-pointer hover:ring-foreground/30 hover:shadow-sm transition-all"
                onClick={() => navigate(`/student/assignment/${assignment.id}`)}
              >
                <CardContent className="p-6">
                  <div className="flex items-start justify-between mb-3">
                    <span className="font-mono text-xs px-2 py-1 bg-secondary text-secondary-foreground rounded">
                      {assignment.subject_code}
                    </span>
                    <Badge className="bg-primary text-primary-foreground gap-1 text-xs">
                      <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse" />
                      LIVE
                    </Badge>
                  </div>
                  <h3 className="font-semibold text-foreground mb-0.5">{assignment.subject_name}</h3>
                  <p className="text-sm text-muted-foreground font-medium">{assignment.assignment_name}</p>

                  <div className="mt-4">
                    <Countdown deadline={assignment.deadline} />
                  </div>

                  <div className="mt-3 flex items-center gap-3 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" />
                      {assignment.time_limit_minutes} min
                    </span>
                    <span className="font-mono">{assignment.allowed_file_types.join(", ")}</span>
                  </div>

                  <p className="text-xs mt-3 font-medium text-primary">Tap to open & submit →</p>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </main>
    </div>
  );
};

export default StudentDashboard;
