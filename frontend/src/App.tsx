function App() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-8 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">Invoice Builder</h1>
      <p className="text-muted-foreground max-w-md text-base">
        Phase 1 scaffold. Auth, routing, and protected screens land in Turn 4.
      </p>
      <a
        className="text-sm text-primary underline-offset-4 hover:underline"
        href="http://localhost:8080/swagger-ui.html"
        target="_blank"
        rel="noreferrer"
      >
        Backend API docs →
      </a>
    </div>
  );
}

export default App;
