import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { studentSubjects, assignments } from "@/data/mock";
import { GraduationCap, LogOut, Wifi, Clock } from "lucide-react";

const StudentDashboard = () => {
  const navigate = useNavigate();

  const getActiveAssignment = (subjectId: string) =>
    assignments.find((a) => a.subjectId === subjectId && a.isActive);

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border bg-card">
        <div className="container flex items-center justify-between h-16 px-6">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
              <GraduationCap className="w-4 h-4 text-primary-foreground" />
            </div>
            <div>
              <h1 className="text-sm font-bold tracking-tight">LabFlow</h1>
              <p className="text-xs text-muted-foreground">Student Portal</p>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={() => navigate("/")}>
            <LogOut className="w-4 h-4 mr-2" />
            Sign Out
          </Button>
        </div>
      </header>

      {/* Content */}
      <main className="container px-6 py-8">
        {/* Active Sessions Banner */}
        <div className="mb-8 p-4 bg-primary text-primary-foreground rounded-lg flex items-center gap-3">
          <Wifi className="w-5 h-5 animate-pulse-dot" />
          <div>
            <p className="text-sm font-semibold">Active Lab Sessions on Network</p>
            <p className="text-xs opacity-80">
              {assignments.filter((a) => a.isActive).length} session(s) currently running
            </p>
          </div>
        </div>

        <div>
          <h2 className="text-2xl font-bold tracking-tight mb-2">My Subjects</h2>
          <p className="text-muted-foreground text-sm mb-6">
            Your registered subjects and active labs
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {studentSubjects.map((subject) => {
            const activeAssignment = getActiveAssignment(subject.id);
            return (
              <Card
                key={subject.id}
                className={`border-border transition-all ${
                  activeAssignment
                    ? "ring-1 ring-foreground/20 cursor-pointer hover:ring-foreground/40"
                    : ""
                }`}
                onClick={() =>
                  activeAssignment &&
                  navigate(`/student/assignment/${activeAssignment.id}`)
                }
              >
                <CardContent className="p-6">
                  <div className="flex items-start justify-between mb-3">
                    <span className="font-mono text-xs px-2 py-1 bg-secondary text-secondary-foreground rounded">
                      {subject.code}
                    </span>
                    {activeAssignment ? (
                      <Badge className="bg-primary text-primary-foreground gap-1 text-xs">
                        <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse-dot" />
                        LIVE
                      </Badge>
                    ) : (
                      <Badge variant="secondary" className="text-xs">
                        No active lab
                      </Badge>
                    )}
                  </div>
                  <h3 className="font-semibold text-foreground">{subject.name}</h3>
                  {activeAssignment ? (
                    <div className="mt-3 flex items-center gap-3 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5" />
                        {activeAssignment.timeLimitMinutes} min remaining
                      </span>
                      <span className="font-medium text-foreground">
                        {activeAssignment.name}
                      </span>
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground mt-3">
                      No assignment is currently active
                    </p>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      </main>
    </div>
  );
};

export default StudentDashboard;
