/**
 * StatsPage.tsx — Reading analytics + yearly goal (UI-01)
 *
 * Focal point: goal progress at top (per UI-SPEC)
 * Sections:
 *  1. Goal progress (Progress bar) — or Set Goal form if unset
 *  2. Books-per-month bar chart (Recharts v2 pattern from PATTERNS.md Pattern 8)
 *  3. All-time stat cards — 2-col grid, Display-size (28px) numbers with muted
 *     14px labels (optional fields via optional chaining — T-06-13)
 *
 * T-06-13: All optional StatsDto fields use optional chaining — absent fields do not crash
 * T-06-14: goalTarget input coerced to number before PUT; backend validates non-negative integer
 * GoalDto has no id — PATTERNS.md was incorrect; api.types.ts has { targetCount, year }
 */
import { useState } from 'react';
import { ResponsiveContainer, BarChart, Bar, XAxis, Tooltip } from 'recharts';
import { useStats, useSetGoal } from '../../hooks/useStats';
import { Card, CardContent } from '../../components/ui/card';
import { Progress } from '../../components/ui/progress';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function StatsPage() {
  const { data: stats, isPending } = useStats();
  const setGoal = useSetGoal();
  const [goalInput, setGoalInput] = useState('');

  if (isPending) {
    return (
      <div className="space-y-4">
        <div className="h-24 bg-muted animate-pulse rounded-lg" />
        <div className="h-48 bg-muted animate-pulse rounded-lg" />
        <div className="h-32 bg-muted animate-pulse rounded-lg" />
      </div>
    );
  }

  if (!stats) return null;

  const hasGoal = stats.goalTarget !== undefined && stats.goalTarget !== null;
  const progressPercent = stats.goalProgressPercent ?? 0;
  const chartData = stats.booksPerMonth.map((count, i) => ({
    month: MONTHS[i],
    books: count,
  }));

  function handleSetGoal() {
    const n = parseInt(goalInput, 10);
    if (!isNaN(n) && n >= 0) {
      setGoal.mutate(n, { onSuccess: () => setGoalInput('') });
    }
  }

  return (
    <div className="pb-16 space-y-6">
      <h1 className="text-[28px] font-semibold">Stats</h1>

      {/* ── Section 1: Goal Progress ── */}
      <section aria-label="Yearly reading goal">
        <h2 className="text-xl font-semibold mb-3">Reading Goal</h2>

        <Card>
          <CardContent className="p-4">
            {hasGoal ? (
              <div className="space-y-2">
                <p className="text-base font-semibold">
                  {stats.booksReadThisYear} of {stats.goalTarget} books this year
                </p>
                <Progress value={progressPercent} className="h-3 [&>[data-slot=progress-indicator]]:bg-accent" />
                <p className="text-sm text-muted-foreground">
                  {progressPercent.toFixed(0)}% complete
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-base text-muted-foreground font-semibold">No yearly goal set</p>
                <p className="text-sm text-muted-foreground">
                  Set a goal above to track how many books you want to read this year.
                </p>
                <div className="flex gap-2 items-end">
                  <div className="flex-1">
                    <Label htmlFor="goal-input" className="text-sm mb-1 block">
                      Books this year
                    </Label>
                    <Input
                      id="goal-input"
                      type="number"
                      min={0}
                      placeholder="e.g. 12"
                      value={goalInput}
                      onChange={(e) => setGoalInput(e.target.value)}
                      className="w-full"
                    />
                  </div>
                  <Button
                    onClick={handleSetGoal}
                    disabled={setGoal.isPending}
                  >
                    Set Goal
                  </Button>
                </div>
              </div>
            )}

            {/* Always offer a way to update the goal */}
            {hasGoal && (
              <div className="mt-4 flex gap-2 items-end">
                <div className="flex-1">
                  <Label htmlFor="update-goal-input" className="text-sm mb-1 block">
                    Update goal
                  </Label>
                  <Input
                    id="update-goal-input"
                    type="number"
                    min={0}
                    placeholder={String(stats.goalTarget)}
                    value={goalInput}
                    onChange={(e) => setGoalInput(e.target.value)}
                    className="w-full"
                  />
                </div>
                <Button
                  onClick={handleSetGoal}
                  disabled={setGoal.isPending || !goalInput}
                  variant="outline"
                >
                  Update Goal
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </section>

      {/* ── Section 2: Books per Month Chart ── */}
      <section aria-label="Books read per month">
        <h2 className="text-xl font-semibold mb-3">This Year</h2>
        <Card>
          <CardContent className="p-4">
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={chartData} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
                <XAxis dataKey="month" tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} />
                <Tooltip />
                <Bar dataKey="books" fill="var(--chart-1)" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </section>

      {/* ── Section 3: Secondary Stats ── */}
      <section aria-label="Reading statistics">
        <h2 className="text-xl font-semibold mb-3">All Time</h2>

        {stats.booksReadThisYear === 0 && stats.booksReadAllTime === 0 ? (
          <div className="text-center py-6">
            <p className="text-base font-semibold text-muted-foreground">Nothing to show yet</p>
            <p className="text-sm text-muted-foreground mt-1">
              Finish your first book this year to unlock stats.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4">
            <Card>
              <CardContent className="p-4">
                <p className="text-[28px] font-semibold">{stats.booksReadAllTime}</p>
                <p className="text-sm text-muted-foreground">Books read (all time)</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-[28px] font-semibold">{stats.currentlyReadingCount}</p>
                <p className="text-sm text-muted-foreground">Currently reading</p>
              </CardContent>
            </Card>
            {stats.averageRating !== undefined && (
              <Card>
                <CardContent className="p-4">
                  <p className="text-[28px] font-semibold">{stats.averageRating.toFixed(1)} / 5</p>
                  <p className="text-sm text-muted-foreground">Average rating</p>
                </CardContent>
              </Card>
            )}
            {stats.pagesReadThisYear !== undefined && (
              <Card>
                <CardContent className="p-4">
                  <p className="text-[28px] font-semibold">{stats.pagesReadThisYear.toLocaleString()}</p>
                  <p className="text-sm text-muted-foreground">Pages read this year</p>
                </CardContent>
              </Card>
            )}
            {stats.averageBookLength !== undefined && (
              <Card>
                <CardContent className="p-4">
                  <p className="text-[28px] font-semibold">{stats.averageBookLength}</p>
                  <p className="text-sm text-muted-foreground">Avg book length (pages)</p>
                </CardContent>
              </Card>
            )}
            {stats.longestBook !== undefined && (
              <Card className="col-span-2">
                <CardContent className="p-4">
                  <p className="text-base font-semibold">
                    {stats.longestBook.title} ({stats.longestBook.pageCount} pp)
                  </p>
                  <p className="text-sm text-muted-foreground">Longest book</p>
                </CardContent>
              </Card>
            )}
            {stats.shortestBook !== undefined && (
              <Card className="col-span-2">
                <CardContent className="p-4">
                  <p className="text-base font-semibold">
                    {stats.shortestBook.title} ({stats.shortestBook.pageCount} pp)
                  </p>
                  <p className="text-sm text-muted-foreground">Shortest book</p>
                </CardContent>
              </Card>
            )}
          </div>
        )}
      </section>

      {setGoal.isError && (
        <p className="text-sm text-destructive">
          {(setGoal.error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
            'Could not save goal. Please try again.'}
        </p>
      )}
    </div>
  );
}
