package com.lumora.cloud.modelgateway.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** One journal per gateway instance, on a persistent volume. Contains accounting metadata only. */
@Component
public class DurableRecoveryJournal implements AutoCloseable {
    public record Entry(RecoveryCommand command, Instant dueAt, String blockedReason, String retryNote) {
        public Entry(RecoveryCommand command, Instant dueAt) { this(command, dueAt, null, null); }
    }
    private final ObjectMapper mapper;
    private final Path directory;
    private final int maximumEntries;
    private final Map<String, Entry> entries = new HashMap<>();
    private final FileChannel ownerChannel;
    private final FileLock ownerLock;

    public DurableRecoveryJournal(ObjectMapper mapper,
            @Value("${lumora.model-gateway.recovery.journal-directory:./data/billing-recovery}") String directory,
            @Value("${lumora.model-gateway.recovery.journal-max-entries:100000}") int maximumEntries) throws IOException {
        this.mapper = mapper;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        if (maximumEntries < 1) throw new IllegalArgumentException("journal-max-entries must be positive");
        this.maximumEntries = maximumEntries;
        Files.createDirectories(this.directory);
        ownerChannel = FileChannel.open(this.directory.resolve(".owner.lock"), CREATE, WRITE);
        FileLock acquired;
        try {
            acquired = ownerChannel.tryLock();
            if (acquired == null) throw new IOException("Recovery journal is already owned by another gateway");
        } catch (RuntimeException | IOException error) {
            ownerChannel.close();
            throw error;
        }
        ownerLock = acquired;
        try (var paths = Files.newDirectoryStream(this.directory, "*.json")) {
            for (Path path : paths) {
                Entry entry = mapper.readValue(Files.readAllBytes(path), Entry.class);
                if (entry.command() == null || entry.dueAt() == null
                        || !path.getFileName().equals(file(entry.command().requestId()).getFileName())) {
                    throw new IOException("Invalid recovery journal entry: " + path.getFileName());
                }
                entries.put(entry.command().requestId(), entry);
            }
        } catch (RuntimeException | IOException error) {
            close();
            throw error; // Preserve unreadable evidence and fail startup, never silently discard it.
        }
    }

    public synchronized void checkWritable() throws IOException {
        requireOpen();
        if (entries.size() >= maximumEntries) throw new IOException("Recovery journal capacity reached");
        Path probe = directory.resolve(".write-probe");
        try (FileChannel channel = FileChannel.open(probe, CREATE, TRUNCATE_EXISTING, WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{1}));
            channel.force(true);
        }
        Files.delete(probe);
    }

    public synchronized void schedule(RecoveryCommand command, Instant dueAt) throws IOException {
        requireOpen();
        Entry old = entries.get(command.requestId());
        if (old != null && !samePayload(old.command(), command)) {
            throw new IOException("Conflicting recovery evidence for request " + command.requestId());
        }
        if (old == null && entries.size() >= maximumEntries) throw new IOException("Recovery journal capacity reached");
        if (old != null && old.command().attempts() > command.attempts()) return;
        if (old != null && old.blockedReason() != null) return;
        Entry entry = new Entry(command, dueAt, null, old == null ? null : old.retryNote());
        write(file(command.requestId()), entry);
        entries.put(command.requestId(), entry);
    }

    public synchronized List<RecoveryCommand> claimDue(Instant now, Instant claimUntil, int limit) {
        requireOpen();
        var due = entries.values().stream().filter(entry -> entry.blockedReason() == null && !entry.dueAt().isAfter(now))
                .sorted(Comparator.comparing(Entry::dueAt).thenComparing(entry -> entry.command().requestId()))
                .limit(limit).toList();
        for (Entry entry : due) entries.put(entry.command().requestId(), new Entry(entry.command(), claimUntil, entry.blockedReason(), entry.retryNote()));
        return due.stream().map(Entry::command).toList();
    }

    public synchronized void complete(RecoveryCommand command) throws IOException {
        requireOpen();
        Entry entry = entries.get(command.requestId());
        if (entry == null || !samePayload(entry.command(), command)) return;
        Files.deleteIfExists(file(command.requestId()));
        syncDirectory();
        entries.remove(command.requestId());
    }

    public synchronized int size() { return entries.size(); }

    public synchronized boolean contains(String requestId) { return entries.containsKey(requestId); }

    public synchronized List<Entry> page(int offset, int limit) {
        return entries.values().stream().sorted(Comparator.comparing(entry -> entry.command().createdAt()))
                .skip(offset).limit(limit).toList();
    }

    public synchronized void park(RecoveryCommand command, String reason) throws IOException {
        requireOpen();
        Entry old = entries.get(command.requestId());
        if (old == null || !samePayload(old.command(), command)) return;
        Entry entry = new Entry(command, Instant.now(), reason, old.retryNote());
        write(file(command.requestId()), entry);
        entries.put(command.requestId(), entry);
    }

    public synchronized void resume(String requestId, String actor, String reason) throws IOException {
        requireOpen();
        Entry old = entries.get(requestId);
        if (old == null) throw new NoSuchElementException("Recovery command not found");
        var command = old.command();
        Entry entry = new Entry(new RecoveryCommand(command.operation(), command.requestId(), command.settlement(),
                command.reason(), 0, command.createdAt()), Instant.now(), null,
                "管理员 #" + actor + " 于 " + Instant.now() + " 恢复投递；依据：" + reason);
        write(file(requestId), entry);
        entries.put(requestId, entry);
    }

    private boolean samePayload(RecoveryCommand left, RecoveryCommand right) {
        return left.operation() == right.operation() && Objects.equals(left.settlement(), right.settlement())
                && Objects.equals(left.reason(), right.reason()) && Objects.equals(left.createdAt(), right.createdAt());
    }

    private Path file(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64)
            throw new IllegalArgumentException("Invalid recovery request ID");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    requestId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private void write(Path target, Entry entry) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(entry);
        Path temporary = directory.resolve(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, CREATE, TRUNCATE_EXISTING, WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        syncDirectory();
    }

    private void syncDirectory() throws IOException {
        // Windows does not expose directory fsync through FileChannel. File contents are forced above.
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) return;
        try (FileChannel channel = FileChannel.open(directory, READ)) { channel.force(true); }
    }

    private void requireOpen() {
        if (!ownerLock.isValid()) throw new IllegalStateException("Recovery journal is closed");
    }

    @Override
    @PreDestroy
    public synchronized void close() throws IOException {
        if (ownerLock.isValid()) ownerLock.release();
        ownerChannel.close();
    }
}
