import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  ArrowLeft, Plus, Play, Clock, FileCode, Upload, Users,
  CheckCircle, Timer, Square, Loader2, Download
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  getSubjectAssignments, createAssignment, startAssignment,
  closeAssignment, getSubmissions, exportCsvUrl,
  type Assignment, type Submission
} from "@/lib/api";
import { getTeacherSubjects, type Subject } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

const SubjectDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { toast } = useToast();

  const [subject, setSubject] = useState<Subject | null>(null);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [submissions, setSubmissions] = useState<Record<string, Submission[]>>({});
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState({
    name: "",
    allowedTypes: ".cpp",
    timeLimit: "120",
    deadline: "",
    sampleFile: null as File | null,
  });

  useEffect(() => {
    if (user && id) {
      fetchSubject();
      fetchAssignments();
    }
  }, [user, id]);

  const fetchSubject = async () => {
    const all = await getTeacherSubjects(user!.id);
    setSubject(all.find(s => s.id === id) ?? null);
  };

  const fetchAssignments = async () => {
    setLoading(true);
    try {
      const data = await getSubjectAssignments(id!);
      setAssignments(data);
      // Fetch submissions for active ones
      data.filter(a => a.is_active).forEach(a => fetchSubs(a.id));
    } finally { setLoading(false); }
  };

  const fetchSubs = async (assignmentId: string) => {
    const subs = await getSubmissions(assignmentId);
    setSubmissions(prev => ({ ...prev, [assignmentId]: subs }));
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name || !form.deadline) {
      toast({ title: "Missing fields", description: "Name and deadline are required.", variant: "destructive" });
      return;
    }
    setCreating(true);
    try {
      const fd = new FormData();
      fd.append("subject_id", id!);
      fd.append("assignment_name", form.name);
      fd.append("allowed_file_types", JSON.stringify(form.allowedTypes.split(",")));
      fd.append("time_limit_minutes", form.timeLimit);
      fd.append("deadline", new Date(form.deadline).toISOString());
      fd.append("created_by", user!.id);
      if (form.sampleFile) fd.append("sample_file", form.sampleFile);
      await createAssignment(fd);
      setDialogOpen(false);
      setForm({ name: "", allowedTypes: ".cpp", timeLimit: "120", deadline: "", sampleFile: null });
      fetchAssignments();
    } catch (err: any) {
      toast({ title: "Error", description: err.message, variant: "destructive" });
    } finally { setCreating(false); }
  };

  const handleStart = async (assignmentId: string) => {
    await startAssignment(assignmentId);
    fetchAssignments();
    toast({ title: "Lab started!", description: "Students can now see and submit this assignment." });
  };

  const handleClose = async (assignmentId: string) => {
    await closeAssignment(assignmentId);
    fetchAssignments();
    toast({ title: "Lab closed", description: "Submissions are now blocked." });
  };

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card">
        <div className="container flex items-center gap-4 h-16 px-6">
          <Button variant="ghost" size="icon" onClick={() => navigate("/staff/dashboard")}>
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-bold">{subject?.subject_name ?? "Loading..."}</h1>
              {subject && (
                <span className="font-mono text-xs px-2 py-0.5 bg-secondary rounded">
                  {subject.subject_code}
                </span>
              )}
            </div>
          </div>
        </div>
      </header>

      <main className="container px-6 py-8">
        <div className="flex items-center justify-between mb-8">
          <h2 className="text-xl font-bold">Assignments</h2>
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-2">
                <Plus className="w-4 h-4" />
                Create Assignment
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
              <DialogHeader>
                <DialogTitle>Create New Assignment</DialogTitle>
              </DialogHeader>
              <form onSubmit={handleCreate} className="space-y-4 mt-4">
                <div className="space-y-2">
                  <Label>Assignment Name</Label>
                  <Input placeholder="e.g. Linked List Implementation" value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
                </div>
                <div className="space-y-2">
                  <Label>Sample File (optional)</Label>
                  <div className="border-2 border-dashed border-border rounded-lg p-4 text-center hover:border-foreground/30 transition-colors cursor-pointer"
                    onClick={() => fileRef.current?.click()}>
                    <Upload className="w-6 h-6 mx-auto text-muted-foreground mb-1" />
                    <p className="text-xs text-muted-foreground">
                      {form.sampleFile ? form.sampleFile.name : "Click to upload sample file"}
                    </p>
                    <input ref={fileRef} type="file" className="hidden"
                      onChange={e => setForm(f => ({ ...f, sampleFile: e.target.files?.[0] ?? null }))} />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Allowed Types</Label>
                    <Select value={form.allowedTypes} onValueChange={v => setForm(f => ({ ...f, allowedTypes: v }))}>
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value=".cpp">.cpp</SelectItem>
                        <SelectItem value=".py">.py</SelectItem>
                        <SelectItem value=".java">.java</SelectItem>
                        <SelectItem value=".cpp,.py">.cpp & .py</SelectItem>
                        <SelectItem value=".cpp,.py,.java">All</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>Time Limit</Label>
                    <Select value={form.timeLimit} onValueChange={v => setForm(f => ({ ...f, timeLimit: v }))}>
                      <SelectTrigger><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="60">1 hour</SelectItem>
                        <SelectItem value="120">2 hours</SelectItem>
                        <SelectItem value="180">3 hours</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div className="space-y-2">
                  <Label>Deadline</Label>
                  <Input type="datetime-local" value={form.deadline}
                    onChange={e => setForm(f => ({ ...f, deadline: e.target.value }))} />
                </div>
                <Button className="w-full gap-2" type="submit" disabled={creating}>
                  {creating ? <><Loader2 className="w-4 h-4 animate-spin" />Creating...</> : <><Plus className="w-4 h-4" />Create Assignment</>}
                </Button>
              </form>
            </DialogContent>
          </Dialog>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-4">
            {assignments.map((assignment) => (
              <Card key={assignment.id} className="border-border">
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <h3 className="font-semibold">{assignment.assignment_name}</h3>
                        {assignment.is_active ? (
                          <Badge className="bg-primary text-primary-foreground gap-1 text-xs">
                            <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse" />
                            LIVE
                          </Badge>
                        ) : (
                          <Badge variant="secondary" className="text-xs">Inactive</Badge>
                        )}
                      </div>
                      <div className="flex items-center gap-4 text-xs text-muted-foreground mt-2">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" />
                          {assignment.time_limit_minutes} min
                        </span>
                        <span className="flex items-center gap-1">
                          <FileCode className="w-3.5 h-3.5" />
                          {assignment.allowed_file_types.join(", ")}
                        </span>
                        {assignment.is_active && (
                          <span className="flex items-center gap-1">
                            <Users className="w-3.5 h-3.5" />
                            {(submissions[assignment.id] ?? []).length} submitted
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      {assignment.is_active ? (
                        <>
                          <Button variant="outline" size="sm" className="gap-1 text-destructive border-destructive/30 hover:bg-destructive/10"
                            onClick={() => handleClose(assignment.id)}>
                            <Square className="w-3.5 h-3.5" />End Lab
                          </Button>
                          <Button variant="outline" size="sm" className="gap-1"
                            onClick={() => window.open(exportCsvUrl(assignment.id), "_blank")}>
                            <Download className="w-3.5 h-3.5" />CSV
                          </Button>
                        </>
                      ) : (
                        <Button size="sm" className="gap-1" onClick={() => handleStart(assignment.id)}>
                          <Play className="w-3.5 h-3.5" />Start Lab
                        </Button>
                      )}
                    </div>
                  </div>

                  {assignment.is_active && (
                    <div className="mt-6 border-t border-border pt-4">
                      <h4 className="text-sm font-medium mb-3 flex items-center gap-2">
                        <CheckCircle className="w-4 h-4" />
                        Submissions ({(submissions[assignment.id] ?? []).length})
                      </h4>
                      <div className="space-y-2">
                        {(submissions[assignment.id] ?? []).map((sub) => (
                          <div key={sub.id} className="flex items-center justify-between py-2 px-3 bg-secondary/50 rounded-md">
                            <div className="flex items-center gap-3">
                              <div className="w-7 h-7 rounded-full bg-primary flex items-center justify-center text-xs text-primary-foreground font-medium">
                                {sub.roll_no.charAt(0)}
                              </div>
                              <div>
                                <p className="text-sm font-medium">{sub.roll_no}</p>
                                <p className="text-xs text-muted-foreground font-mono">
                                  {sub.file_path.split("/").pop()}
                                </p>
                              </div>
                            </div>
                            <div className="flex items-center gap-1 text-xs text-muted-foreground">
                              <Timer className="w-3 h-3" />
                              {new Date(sub.submitted_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                            </div>
                          </div>
                        ))}
                        {(submissions[assignment.id] ?? []).length === 0 && (
                          <p className="text-xs text-center text-muted-foreground py-4">No submissions yet</p>
                        )}
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}

            {assignments.length === 0 && (
              <div className="text-center py-16 text-muted-foreground">
                <FileCode className="w-12 h-12 mx-auto mb-4 opacity-30" />
                <p className="text-sm">No assignments yet</p>
                <p className="text-xs mt-1">Create your first assignment to get started</p>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
};

export default SubjectDetail;
