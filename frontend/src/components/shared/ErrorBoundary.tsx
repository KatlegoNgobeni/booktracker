/**
 * ErrorBoundary.tsx — Top-level React error boundary (CR-01 follow-up)
 *
 * Before this existed, any uncaught render exception unmounted the entire
 * React tree, leaving a permanent white screen. This boundary catches render
 * errors anywhere below it and shows a recoverable fallback instead.
 *
 * Mounted once in main.tsx around <App />. Class component because React
 * error boundaries require componentDidCatch/getDerivedStateFromError.
 */
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Surface to console for debugging; no telemetry backend exists yet.
    console.error('Uncaught render error:', error, info.componentStack);
  }

  handleReload = () => {
    // Full reload from the origin root — recovers from bad URLs too.
    window.location.assign('/');
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background px-4 text-center">
          <h1 className="text-lg font-semibold text-foreground">Something went wrong</h1>
          <p className="text-sm text-muted-foreground">
            An unexpected error occurred. Reloading usually fixes it.
          </p>
          <button
            className="rounded-md border border-input bg-background px-4 py-2 text-sm font-semibold text-foreground hover:bg-muted hover:text-foreground transition-colors"
            onClick={this.handleReload}
          >
            Reload app
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
