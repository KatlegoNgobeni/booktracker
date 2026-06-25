package com.booktracker.shelf;

import com.booktracker.books.BookEntity;
import com.booktracker.books.BookRepository;
import com.booktracker.books.BookService;
import com.booktracker.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShelfService} using Mockito without a Spring context.
 *
 * <p>Plan 01 scaffold: basic wiring test.
 * Plan 02 additions: auto-date rule tests (D-10/D-11/D-12), null-preserve, ownership 403.
 */
@ExtendWith(MockitoExtension.class)
class ShelfServiceTest {

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private BookService bookService;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private ShelfService shelfService;

    private UserEntity user;
    private UUID entryId;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(UUID.randomUUID());
        entryId = UUID.randomUUID();
    }

    /**
     * Scaffold placeholder from Plan 01 — verifies @InjectMocks wiring compiles and runs.
     */
    @Test
    void addToShelf_persistsAndReturnsDto() {
        assertThat(shelfService).isNotNull();
    }

    // ----------------------------------------------------------------
    // D-10: Status → READ auto-sets dateFinished if absent
    // ----------------------------------------------------------------

    /**
     * D-10 (set): Entry has dateFinished=null, status update to READ.
     * Expected: dateFinished is set to today.
     */
    @Test
    void autoDate_readSetsDateFinishedWhenAbsent() {
        UserBookEntity entry = buildEntry(ShelfStatus.WANT_TO_READ);
        entry.setDateFinished(null);

        when(shelfRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(shelfRepository.save(any(UserBookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShelfRequest req = new UpdateShelfRequest();
        req.setStatus(ShelfStatus.READ);

        ShelfEntryDto result = shelfService.updateMetadata(entryId, req, user);

        assertThat(result).isNotNull();
        assertThat(entry.getDateFinished()).isEqualTo(LocalDate.now());
    }

    /**
     * D-10 (preserve): Entry already has dateFinished set to a past date.
     * Expected: dateFinished is NOT overwritten.
     */
    @Test
    void autoDate_readPreservesExistingDateFinished() {
        LocalDate existingDate = LocalDate.of(2026, 1, 15);
        UserBookEntity entry = buildEntry(ShelfStatus.WANT_TO_READ);
        entry.setDateFinished(existingDate);

        when(shelfRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(shelfRepository.save(any(UserBookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShelfRequest req = new UpdateShelfRequest();
        req.setStatus(ShelfStatus.READ);

        shelfService.updateMetadata(entryId, req, user);

        assertThat(entry.getDateFinished()).isEqualTo(existingDate);
    }

    // ----------------------------------------------------------------
    // D-11: Status → CURRENTLY_READING auto-sets dateStarted if absent
    // ----------------------------------------------------------------

    /**
     * D-11: Entry has dateStarted=null, status update to CURRENTLY_READING.
     * Expected: dateStarted is set to today.
     */
    @Test
    void autoDate_currentlyReadingSetsDateStartedWhenAbsent() {
        UserBookEntity entry = buildEntry(ShelfStatus.WANT_TO_READ);
        entry.setDateStarted(null);

        when(shelfRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(shelfRepository.save(any(UserBookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShelfRequest req = new UpdateShelfRequest();
        req.setStatus(ShelfStatus.CURRENTLY_READING);

        shelfService.updateMetadata(entryId, req, user);

        assertThat(entry.getDateStarted()).isEqualTo(LocalDate.now());
    }

    // ----------------------------------------------------------------
    // D-12: Status downgraded from READ clears dateFinished
    // ----------------------------------------------------------------

    /**
     * D-12: Entry has oldStatus=READ with dateFinished set, status changes to WANT_TO_READ.
     * Expected: dateFinished is cleared (null) to prevent stale stats data.
     */
    @Test
    void autoDate_downgradeFromReadClearsDateFinished() {
        UserBookEntity entry = buildEntry(ShelfStatus.READ);
        entry.setDateFinished(LocalDate.of(2026, 3, 10));

        when(shelfRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(shelfRepository.save(any(UserBookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShelfRequest req = new UpdateShelfRequest();
        req.setStatus(ShelfStatus.WANT_TO_READ);

        shelfService.updateMetadata(entryId, req, user);

        assertThat(entry.getDateFinished()).isNull();
    }

    // ----------------------------------------------------------------
    // Null-preserve: null request rating preserves existing rating
    // ----------------------------------------------------------------

    /**
     * Null-preserve: request rating=null, existing rating=4.
     * Expected: rating stays 4 (null means preserve-existing, not clear).
     */
    @Test
    void updateMetadata_nullRatingPreservesExisting() {
        UserBookEntity entry = buildEntry(ShelfStatus.WANT_TO_READ);
        entry.setRating(4);

        when(shelfRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(shelfRepository.save(any(UserBookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShelfRequest req = new UpdateShelfRequest();
        // rating left null — should preserve existing

        shelfService.updateMetadata(entryId, req, user);

        assertThat(entry.getRating()).isEqualTo(4);
    }

    // ----------------------------------------------------------------
    // Ownership: wrong owner throws 403 Forbidden
    // ----------------------------------------------------------------

    /**
     * T-04-06 IDOR: Caller user ID does not match entry.getUser().getId().
     * Expected: ResponseStatusException with 403 FORBIDDEN.
     */
    @Test
    void updateMetadata_wrongOwnerThrowsForbidden() {
        // Entry belongs to a different user
        UserEntity otherUser = new UserEntity();
        otherUser.setId(UUID.randomUUID());

        UserBookEntity entry = buildEntryOwnedBy(otherUser, ShelfStatus.WANT_TO_READ);

        when(shelfRepository.findById(entryId)).thenReturn(Optional.of(entry));

        UpdateShelfRequest req = new UpdateShelfRequest();
        req.setStatus(ShelfStatus.READ);

        assertThatThrownBy(() -> shelfService.updateMetadata(entryId, req, user))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Build a UserBookEntity owned by the test {@link #user} with the given status.
     * Attaches a minimal BookEntity for toDto() mapping.
     */
    private UserBookEntity buildEntry(ShelfStatus status) {
        return buildEntryOwnedBy(user, status);
    }

    private UserBookEntity buildEntryOwnedBy(UserEntity owner, ShelfStatus status) {
        UserBookEntity entry = new UserBookEntity();
        entry.setId(entryId);
        entry.setUser(owner);
        entry.setShelfStatus(status);

        BookEntity book = new BookEntity();
        book.setId(UUID.randomUUID());
        book.setOpenLibraryKey("/works/OL99999W");
        book.setTitle("Test Book");
        book.setAuthors("Test Author");
        book.setCoverId("12345");
        entry.setBook(book);

        return entry;
    }
}
