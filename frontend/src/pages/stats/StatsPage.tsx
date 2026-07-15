/**
 * StatsPage.tsx — Reading analytics + yearly goal (UI-01)
 *
 * Focal point: goal progress at top (per UI-SPEC)
 * Sections:
 *  1. Goal progress (Progress bar) — or Set Goal form if unset
 *     STATS-04: ahead/behind-schedule verdict when hasGoal
 *     STATS-05: pages-per-day current pace when hasGoal + pagesReadThisYear
 *  2. Books-per-month bar chart (Recharts v2 pattern from PATTERNS.md Pattern 8)
 *     STATS-03: ReferenceLine at goalTarget/12 when hasGoal
 *     STATS-06: "Nothing to show yet" empty state when booksReadThisYear === 0
 *  3. Reading streaks (STATS-01/02)
 *     STATS-01: flame callout card when currentStreakDays > 0
 *     STATS-02: motivational empty state when currentStreakDays === 0
 *  4. All-time stat cards — 2-col grid, Display-size (28px) numbers with muted
 *     14px labels (optional fields via optional chaining — T-06-13)
 *     STATS-08: topGenre card (col-span-2) when stats.topGenre is present
 *
 * T-06-13: All optional StatsDto fields use optional chaining — absent fields do not crash
 * T-06-14: goalTarget input coerced to number before PUT; backend validates non-negative integer
 * GoalDto has no id — PATTERNS.md was incorrect; api.types.ts has { targetCount, year }
 */
import { useState } from 'react';
import { ResponsiveContainer, BarChart, Bar, XAxis, Tooltip, ReferenceLine } from 'recharts';
import { Flame } from 'lucide-react';
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

  // 0-based month index; aligns with booksPerMonth[0]=Jan
  const currentMonthIndex = new Date().getMonth();

  // STATS-04: ahead/behind verdict — computed when hasGoal is true
  const verdict = hasGoal && stats.goalTarget != null
    ? (() => {
        const monthsElapsed = currentMonthIndex + 1;
        const expected = Math.floor((monthsElapsed / 12) * stats.goalTarget!);
        const delta = Number(stats.booksReadThisYear) - expected;
        if (delta > 0) return `${delta} book${delta !== 1 ? 's' : ''} ahead of schedule`;
        if (delta < 0) return `${Math.abs(delta)} book${Math.abs(delta) !== 1 ? 's' : ''} behind schedule`;
        return 'On track';
      })()
    : null;

  // STATS-05: pages-per-day current pace — shown when hasGoal and pagesReadThisYear present
  const pagesPerDay = hasGoal && stats.pagesReadThisYear != null
    ? (() => {
        const dayOfYear = Math.ceil(
          (Date.now() - new Date(new Date().getFullYear(), 0, 0).getTime()) / 86_400_000
        );
        return (stats.pagesReadThisYear / Math.max(dayOfYear, 1)).toFixed(1);
      })()
    : null;

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
                {/* STATS-04: ahead/behind verdict */}
                {verdict && (
                  <p className="text-sm text-muted-foreground">{verdict}</p>
                )}
                {/* STATS-05: pages-per-day pace */}
                {pagesPerDay != null && (
                  <p className="text-sm text-muted-foreground">{pagesPerDay} pages/day</p>
                )}
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
            {/* STATS-06: empty state for fresh/sparse accounts — no flat zero-line as first impression */}
            {stats.booksReadThisYear === 0 ? (
              <div className="text-center py-6">
                <p className="text-base font-semibold text-muted-foreground">Nothing to show yet</p>
                <p className="text-sm text-muted-foreground mt-1">
                  Finish your first book this year to start tracking pace.
                </p>
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={chartData} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
                  <XAxis dataKey="month" tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }} />
                  <Tooltip />
                  <Bar dataKey="books" fill="var(--chart-1)" radius={[3, 3, 0, 0]} />
                  {/* STATS-03: reference line at monthly goal pace */}
                  {hasGoal && stats.goalTarget != null && (
                    <ReferenceLine
                      y={stats.goalTarget / 12}
                      stroke="var(--muted-foreground)"
                      strokeDasharray="4 4"
                      label={{ value: 'Goal pace', position: 'insideTopRight', fontSize: 10 }}
                    />
                  )}
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>
      </section>

      {/* ── Section 3: Reading Streaks (STATS-01/02) ── */}
      {/* Always renders — streak state communicates something meaningful even at 0 */}
      <section aria-label="Reading streaks">
        <h2 className="text-xl font-semibold mb-3">Streaks</h2>
        {/* STATS-01: flame callout when active streak; STATS-02: motivational empty state when idle */}
        {stats.currentStreakDays > 0 ? (
          <Card className="border-orange-200 dark:border-orange-800">
            <CardContent className="p-4 flex items-center gap-4">
              <Flame className="h-10 w-10 text-orange-500 flex-shrink-0" aria-hidden="true" />
              <div>
                <p className="text-[32px] font-bold text-orange-500 leading-none">
                  {stats.currentStreakDays}
                </p>
                <p className="text-sm font-medium">day streak</p>
                {stats.longestStreakDays > stats.currentStreakDays && (
                  <p className="text-xs text-muted-foreground">
                    Best: {stats.longestStreakDays} days
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardContent className="p-4 text-center space-y-1">
              <p className="text-base font-semibold text-muted-foreground">No active streak</p>
              {stats.longestStreakDays > 0 ? (
                <p className="text-sm text-muted-foreground">
                  Your best: {stats.longestStreakDays} days — read today to beat it!
                </p>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Read today to start your first streak!
                </p>
              )}
            </CardContent>
          </Card>
        )}
      </section>

      {/* ── Section 4: Secondary Stats ── */}
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
            {/* STATS-08: topGenre card — only rendered when subject data is available */}
            {stats.topGenre != null && (
              <Card className="col-span-2">
                <CardContent className="p-4">
                  <p className="text-base font-semibold">{stats.topGenre}</p>
                  <p className="text-sm text-muted-foreground">Top genre this year</p>
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
