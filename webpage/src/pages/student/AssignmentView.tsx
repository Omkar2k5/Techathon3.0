import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { assignments } from "@/data/mock";
import {
  ArrowLeft, Download, Upload, Clock, FileCode, CheckCircle, AlertCircle
} from "lucide-react";

const AssignmentView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const assignment = assignments.find((a) => a.id === id);
  const [uploaded, setUploaded] = useState(false);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);

  if (!assignment) return null;

  const handleFileSelect = () => {
    setSelectedFile("my_solution.cpp");
  };

  const handleUpload = () => {
    setUploaded(true);
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border bg-card">
        <div className="container flex items-center gap-4 h-16 px-6">
          <Button variant="ghost" size="icon" onClick={() => navigate("/student/dashboard")}>
            <ArrowLeft className="w-4 h-4" />
          </Button>
          <div className="flex-1">
            <h1 className="text-sm font-bold">{assignment.name}</h1>
            <p className="text-xs text-muted-foreground">Active Lab Session</p>
          </div>
          <Badge className="bg-primary text-primary-foreground gap-1">
            <span className="w-1.5 h-1.5 bg-primary-foreground rounded-full animate-pulse-dot" />
            LIVE
          </Badge>
        </div>
      </header>

      <main className="container px-6 py-8 max-w-2xl mx-auto">
        {/* Timer */}
        <div className="mb-6 p-4 bg-secondary rounded-lg flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Clock className="w-5 h-5 text-foreground" />
            <span className="text-sm font-medium">Time Remaining</span>
          </div>
          <span className="font-mono text-xl font-bold text-foreground">
            01:45:30
          </span>
        </div>

        {/* Instructions */}
        <Card className="border-border mb-6">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Assignment Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">Allowed Types</span>
              <div className="flex gap-1">
                {assignment.allowedTypes.map((t) => (
                  <span key={t} className="font-mono text-xs px-2 py-0.5 bg-secondary rounded">
                    {t}
                  </span>
                ))}
              </div>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">Time Limit</span>
              <span className="font-medium">{assignment.timeLimitMinutes} minutes</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">Sample File</span>
              <span className="font-mono text-xs">{assignment.sampleFileName}</span>
            </div>
          </CardContent>
        </Card>

        {/* Step 1: Download */}
        <Card className="border-border mb-4">
          <CardContent className="p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-primary-foreground text-sm font-bold">
                1
              </div>
              <div>
                <h3 className="font-semibold text-sm">Download Sample File</h3>
                <p className="text-xs text-muted-foreground">
                  Get the template to work on
                </p>
              </div>
            </div>
            <Button variant="outline" className="w-full gap-2">
              <Download className="w-4 h-4" />
              Download {assignment.sampleFileName}
            </Button>
          </CardContent>
        </Card>

        {/* Step 2: Upload */}
        <Card className={`border-border ${uploaded ? "border-foreground/20" : ""}`}>
          <CardContent className="p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center text-primary-foreground text-sm font-bold">
                2
              </div>
              <div>
                <h3 className="font-semibold text-sm">Upload Your Solution</h3>
                <p className="text-xs text-muted-foreground">
                  Submit before the deadline
                </p>
              </div>
            </div>

            {uploaded ? (
              <div className="text-center py-6">
                <CheckCircle className="w-12 h-12 mx-auto text-foreground mb-3" />
                <p className="font-semibold text-sm">Submitted Successfully</p>
                <p className="text-xs text-muted-foreground mt-1">
                  my_solution.cpp uploaded at {new Date().toLocaleTimeString()}
                </p>
              </div>
            ) : (
              <>
                <div
                  className="border-2 border-dashed border-border rounded-lg p-8 text-center hover:border-foreground/30 transition-colors cursor-pointer mb-4"
                  onClick={handleFileSelect}
                >
                  {selectedFile ? (
                    <div className="flex items-center justify-center gap-2">
                      <FileCode className="w-5 h-5 text-foreground" />
                      <span className="font-mono text-sm">{selectedFile}</span>
                    </div>
                  ) : (
                    <>
                      <Upload className="w-8 h-8 mx-auto text-muted-foreground mb-2" />
                      <p className="text-sm text-muted-foreground">
                        Click to select your file
                      </p>
                      <p className="text-xs text-muted-foreground mt-1">
                        {assignment.allowedTypes.join(", ")} only
                      </p>
                    </>
                  )}
                </div>
                <Button
                  className="w-full gap-2"
                  disabled={!selectedFile}
                  onClick={handleUpload}
                >
                  <Upload className="w-4 h-4" />
                  Submit Solution
                </Button>
              </>
            )}
          </CardContent>
        </Card>

        {/* Warning */}
        <div className="mt-6 flex items-start gap-2 text-xs text-muted-foreground">
          <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <p>
            Make sure to submit before the timer expires. Late submissions will
            not be accepted. Only files with allowed extensions will be accepted.
          </p>
        </div>
      </main>
    </div>
  );
};

export default AssignmentView;
