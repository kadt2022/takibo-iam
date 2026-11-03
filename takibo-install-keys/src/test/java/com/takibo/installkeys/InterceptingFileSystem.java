package com.takibo.installkeys;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Système de fichiers de test : délègue chaque opération au disque réel, mais laisse un test
 * intervenir <b>juste avant</b> chacune d'elles (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Deux usages, que le disque local ne permet ni l'un ni l'autre d'observer :
 * <ul>
 *   <li>simuler une panne précise — disque plein à l'écriture, volume sans liens physiques,
 *       répertoire qui refuse d'être forcé, volume à ACL au lieu de POSIX ;</li>
 *   <li>figer un fil à un instant exact pour rejouer une course de façon déterministe,
 *       là où des fils libres ne la produisent qu'au hasard de l'ordonnanceur.</li>
 * </ul>
 * Les fichiers existent réellement sur le disque : les assertions se font sur les chemins
 * réels, jamais à travers ce double.
 */
final class InterceptingFileSystem extends FileSystem {

    /** Les opérations que {@link SecretFileWriter} adresse au système de fichiers. */
    enum Operation {
        EXISTS, SAME_FILE, CREATE_FILE, OPEN_CHANNEL, CREATE_LINK, DELETE, FILE_STORE
    }

    /** Appelé avant l'opération réelle ; peut lever, bloquer, ou agir sur le disque. */
    @FunctionalInterface
    interface Interceptor {
        void before(Operation operation, Path realPath) throws IOException;
    }

    private final FileSystem real;
    private final Provider provider = new Provider();
    private volatile Interceptor interceptor = (operation, path) -> { };
    private volatile Set<String> supportedViews;
    private volatile AclFileAttributeView aclView;

    InterceptingFileSystem(Path anyRealPath) {
        this.real = anyRealPath.getFileSystem();
    }

    /** Le même chemin, mais dont chaque opération passe par l'intercepteur. */
    Path wrap(Path realPath) {
        return new InterceptedPath(this, realPath);
    }

    InterceptingFileSystem intercept(Interceptor newInterceptor) {
        this.interceptor = newInterceptor;
        return this;
    }

    /** Fait déclarer au volume ces seules vues d'attributs ({@code "posix"}, {@code "acl"}…). */
    InterceptingFileSystem declaringViews(String... views) {
        this.supportedViews = Set.of(views);
        return this;
    }

    /** Vue ACL et propriétaire rendus pour tout fichier — le volume Windows simulé. */
    InterceptingFileSystem withAclView(AclFileAttributeView view) {
        this.aclView = view;
        return this;
    }

    private static Path unwrap(Path path) {
        return path instanceof InterceptedPath intercepted ? intercepted.delegate : path;
    }

    private void before(Operation operation, Path path) throws IOException {
        interceptor.before(operation, unwrap(path));
    }

    // ---------- FileSystem ----------

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return real.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return real.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        return wrap(real.getPath(first, more));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return real.getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return real.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    // ---------- Provider ----------

    private final class Provider extends FileSystemProvider {

        private FileSystemProvider realProvider() {
            return real.provider();
        }

        @Override
        public String getScheme() {
            return "intercepted";
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path getPath(URI uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                                  FileAttribute<?>... attrs) throws IOException {
            if (options.contains(StandardOpenOption.CREATE_NEW)) {
                before(Operation.CREATE_FILE, path);
            } else {
                before(Operation.OPEN_CHANNEL, path);
            }
            return realProvider().newByteChannel(unwrap(path), options, attrs);
        }

        @Override
        public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options,
                                          FileAttribute<?>... attrs) throws IOException {
            before(Operation.OPEN_CHANNEL, path);
            return realProvider().newFileChannel(unwrap(path), options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                        DirectoryStream.Filter<? super Path> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            before(Operation.CREATE_LINK, link);
            realProvider().createLink(unwrap(link), unwrap(existing));
        }

        @Override
        public void delete(Path path) throws IOException {
            before(Operation.DELETE, path);
            realProvider().delete(unwrap(path));
        }

        @Override
        public boolean deleteIfExists(Path path) throws IOException {
            before(Operation.DELETE, path);
            return realProvider().deleteIfExists(unwrap(path));
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSameFile(Path path, Path path2) throws IOException {
            before(Operation.SAME_FILE, path);
            return realProvider().isSameFile(unwrap(path), unwrap(path2));
        }

        @Override
        public boolean isHidden(Path path) throws IOException {
            return realProvider().isHidden(unwrap(path));
        }

        @Override
        public FileStore getFileStore(Path path) throws IOException {
            before(Operation.FILE_STORE, path);
            return new DeclaringFileStore(realProvider().getFileStore(unwrap(path)));
        }

        @Override
        public boolean exists(Path path, LinkOption... options) {
            try {
                before(Operation.EXISTS, path);
            } catch (IOException e) {
                return false;
            }
            return realProvider().exists(unwrap(path), options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributesIfExists(
                Path path, Class<A> type, LinkOption... options) throws IOException {
            return realProvider().readAttributesIfExists(unwrap(path), type, options);
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
            realProvider().checkAccess(unwrap(path), modes);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
                                                                    LinkOption... options) {
            AclFileAttributeView acl = aclView;
            if (acl != null && (type == AclFileAttributeView.class
                    || type == FileOwnerAttributeView.class)) {
                return (V) acl;
            }
            return realProvider().getFileAttributeView(unwrap(path), type, options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
                                                                LinkOption... options)
                throws IOException {
            return realProvider().readAttributes(unwrap(path), type, options);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes,
                                                  LinkOption... options) throws IOException {
            return realProvider().readAttributes(unwrap(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value,
                                 LinkOption... options) throws IOException {
            realProvider().setAttribute(unwrap(path), attribute, value, options);
        }
    }

    /** Le volume réel, dont seules les capacités déclarées peuvent être réécrites. */
    private final class DeclaringFileStore extends FileStore {

        private final FileStore delegate;

        DeclaringFileStore(FileStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean supportsFileAttributeView(String name) {
            Set<String> views = supportedViews;
            return views == null ? delegate.supportsFileAttributeView(name) : views.contains(name);
        }

        @Override
        public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
            return delegate.supportsFileAttributeView(type);
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String type() {
            return delegate.type();
        }

        @Override
        public boolean isReadOnly() {
            return delegate.isReadOnly();
        }

        @Override
        public long getTotalSpace() throws IOException {
            return delegate.getTotalSpace();
        }

        @Override
        public long getUsableSpace() throws IOException {
            return delegate.getUsableSpace();
        }

        @Override
        public long getUnallocatedSpace() throws IOException {
            return delegate.getUnallocatedSpace();
        }

        @Override
        public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
            return delegate.getFileStoreAttributeView(type);
        }

        @Override
        public Object getAttribute(String attribute) throws IOException {
            return delegate.getAttribute(attribute);
        }
    }

    /** Un chemin réel, rattaché à ce système de fichiers pour que ses opérations y passent. */
    private static final class InterceptedPath implements Path {

        private final InterceptingFileSystem fileSystem;
        private final Path delegate;

        InterceptedPath(InterceptingFileSystem fileSystem, Path delegate) {
            this.fileSystem = fileSystem;
            this.delegate = delegate;
        }

        private Path wrapNullable(Path path) {
            return path == null ? null : new InterceptedPath(fileSystem, path);
        }

        @Override
        public FileSystem getFileSystem() {
            return fileSystem;
        }

        @Override
        public boolean isAbsolute() {
            return delegate.isAbsolute();
        }

        @Override
        public Path getRoot() {
            return wrapNullable(delegate.getRoot());
        }

        @Override
        public Path getFileName() {
            return wrapNullable(delegate.getFileName());
        }

        @Override
        public Path getParent() {
            return wrapNullable(delegate.getParent());
        }

        @Override
        public int getNameCount() {
            return delegate.getNameCount();
        }

        @Override
        public Path getName(int index) {
            return wrapNullable(delegate.getName(index));
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
            return wrapNullable(delegate.subpath(beginIndex, endIndex));
        }

        @Override
        public boolean startsWith(Path other) {
            return delegate.startsWith(unwrap(other));
        }

        @Override
        public boolean endsWith(Path other) {
            return delegate.endsWith(unwrap(other));
        }

        @Override
        public Path normalize() {
            return wrapNullable(delegate.normalize());
        }

        @Override
        public Path resolve(Path other) {
            return wrapNullable(delegate.resolve(unwrap(other)));
        }

        @Override
        public Path relativize(Path other) {
            return wrapNullable(delegate.relativize(unwrap(other)));
        }

        @Override
        public URI toUri() {
            return delegate.toUri();
        }

        @Override
        public Path toAbsolutePath() {
            return wrapNullable(delegate.toAbsolutePath());
        }

        @Override
        public Path toRealPath(LinkOption... options) throws IOException {
            return wrapNullable(delegate.toRealPath(options));
        }

        @Override
        public File toFile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
                                 WatchEvent.Modifier... modifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Path other) {
            return delegate.compareTo(unwrap(other));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InterceptedPath path && delegate.equals(path.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delegate);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
