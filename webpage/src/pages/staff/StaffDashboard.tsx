import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { BookOpen, Users, LogOut, Plus, Loader2, Bot } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { getTeacherSubjects, createSubject, type Subject } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

const StaffDashboard = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { toast } = useToast();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ subject_name: "", subject_code: "" });

  useEffect(() => {
    if (user) fetchSubjects();
  }, [user]);

  const fetchSubjects = async () => {
    setLoading(true);
    try {
      const data = await getTeacherSubjects(user!.id);
      setSubjects(data);
    } catch {
      toast({ title: "Error", description: "Failed to load subjects.", variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.subject_name || !form.subject_code) return;
    setCreating(true);
    try {
      await createSubject(form.subject_name, form.subject_code, user!.id);
      setDialogOpen(false);
      setForm({ subject_name: "", subject_code: "" });
      fetchSubjects();
    } catch (err: any) {
      toast({ title: "Error", description: err.message, variant: "destructive" });
    } finally {
      setCreating(false);
    }
  };

  const handleLogout = () => { logout(); navigate("/"); };

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card">
        <div className="container flex items-center justify-between h-16 px-6">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
              <BookOpen className="w-4 h-4 text-primary-foreground" />
            </div>
            <div>
              <h1 className="text-sm font-bold tracking-tight">EduNet</h1>
              <p className="text-xs text-muted-foreground">Staff Portal · {user?.name}</p>
            </div>
          </div>
          <Button variant="ghost" size="sm" className="gap-2" onClick={() => navigate("/chat")}>
            <Bot className="w-4 h-4" />
            Assistant
          </Button>
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            <LogOut className="w-4 h-4 mr-2" />
            Sign Out
          </Button>
        </div>
      </header>

      <main className="container px-6 py-8">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">My Subjects</h2>
            <p className="text-muted-foreground text-sm mt-1">Select a subject to manage assignments</p>
          </div>
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-2">
                <Plus className="w-4 h-4" />
                New Subject
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-sm">
              <DialogHeader>
                <DialogTitle>Add New Subject</DialogTitle>
              </DialogHeader>
              <form onSubmit={handleCreate} className="space-y-4 mt-2">
                <div className="space-y-2">
                  <Label>Subject Name</Label>
                  <Input placeholder="e.g. Data Structures" value={form.subject_name}
                    onChange={e => setForm(f => ({ ...f, subject_name: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>Subject Code</Label>
                  <Input placeholder="e.g. CS301" value={form.subject_code}
                    onChange={e => setForm(f => ({ ...f, subject_code: e.target.value }))} />
                </div>
                <Button type="submit" className="w-full" disabled={creating}>
                  {creating ? <><Loader2 className="w-4 h-4 mr-2 animate-spin" />Creating...</> : "Add Subject"}
                </Button>
              </form>
            </DialogContent>
          </Dialog>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : subjects.length === 0 ? (
          <div className="text-center py-20 text-muted-foreground">
            <BookOpen className="w-12 h-12 mx-auto mb-4 opacity-30" />
            <p className="text-sm font-medium">No subjects yet</p>
            <p className="text-xs mt-1">Create your first subject to get started</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {subjects.map((subject) => (
              <Card key={subject.id}
                className="border-border hover:border-foreground/20 transition-colors cursor-pointer group"
                onClick={() => navigate(`/staff/subject/${subject.id}`)}>
                <CardContent className="p-6">
                  <div className="flex items-start justify-between mb-4">
                    <span className="font-mono text-xs px-2 py-1 bg-secondary text-secondary-foreground rounded">
                      {subject.subject_code}
                    </span>
                    <Users className="w-4 h-4 text-muted-foreground" />
                  </div>
                  <h3 className="font-semibold text-foreground group-hover:text-foreground/80 transition-colors">
                    {subject.subject_name}
                  </h3>
                  <p className="text-xs text-muted-foreground mt-2">Click to manage assignments →</p>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </main>
    </div>
  );
};

export default StaffDashboard;
