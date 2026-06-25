package com.booktracker.goal;

import com.booktracker.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoalService} using Mockito without a Spring context.
 *
 * <p>Covers STATS-01 upsert logic:
 * <ul>
 *   <li>Create new goal when none exists for current year</li>
 *   <li>Update existing goal (same row, no new row created)</li>
 *   <li>getGoalForCurrentYear throws 404 when no goal is set</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private GoalService goalService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(UUID.randomUUID());
    }

    /**
     * STATS-01 (create): When no goal exists for the current year, upsertGoal creates a
     * new GoalEntity and invokes repository.save() once.
     */
    @Test
    void upsertGoal_noExistingGoal_createsNewGoalEntity() {
        // Arrange: repository finds nothing for user + current year
        when(goalRepository.findByUserIdAndYear(eq(user.getId()), anyInt()))
                .thenReturn(Optional.empty());

        GoalEntity savedGoal = new GoalEntity();
        savedGoal.setId(UUID.randomUUID());
        savedGoal.setUser(user);
        savedGoal.setYear(2026);
        savedGoal.setTargetCount(12);
        when(goalRepository.save(any(GoalEntity.class))).thenReturn(savedGoal);

        // Act
        GoalDto result = goalService.upsertGoal(12, user);

        // Assert: save() invoked once and DTO is returned
        verify(goalRepository, times(1)).save(any(GoalEntity.class));
        assertThat(result).isNotNull();
        assertThat(result.targetCount()).isEqualTo(12);
    }

    /**
     * STATS-01 (update): When a goal already exists for the current year, upsertGoal
     * updates the existing entity's targetCount (no second row is created).
     */
    @Test
    void upsertGoal_existingGoal_updatesTargetCountOnSameEntity() {
        // Arrange: existing goal with targetCount=5
        GoalEntity existingGoal = new GoalEntity();
        existingGoal.setId(UUID.randomUUID());
        existingGoal.setUser(user);
        existingGoal.setYear(2026);
        existingGoal.setTargetCount(5);

        when(goalRepository.findByUserIdAndYear(eq(user.getId()), anyInt()))
                .thenReturn(Optional.of(existingGoal));
        when(goalRepository.save(any(GoalEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: update to targetCount=20
        GoalDto result = goalService.upsertGoal(20, user);

        // Assert: save() called once (not twice), same entity updated
        verify(goalRepository, times(1)).save(existingGoal);
        assertThat(existingGoal.getTargetCount()).isEqualTo(20);
        assertThat(result.targetCount()).isEqualTo(20);
    }

    /**
     * STATS-01: getGoalForCurrentYear throws ResponseStatusException(404) when
     * repository.findByUserIdAndYear returns empty.
     */
    @Test
    void getGoalForCurrentYear_noGoal_throwsNotFound() {
        // Arrange: no goal exists
        when(goalRepository.findByUserIdAndYear(eq(user.getId()), anyInt()))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> goalService.getGoalForCurrentYear(user))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
