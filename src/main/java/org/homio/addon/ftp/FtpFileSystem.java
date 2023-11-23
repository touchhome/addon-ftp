package org.homio.addon.ftp;

import static org.apache.commons.net.ftp.FTPFile.DIRECTORY_TYPE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.homio.addon.ftp.FtpFileSystem.FtpFile;
import org.homio.addon.ftp.FtpFileSystem.FtpFileService;
import org.homio.api.Context;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.BaseCachedFileSystemProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FtpFileSystem extends BaseCachedFileSystemProvider<FtpEntity, FtpFile, FtpFileService> {

  public FtpFileSystem(FtpEntity entity, Context context) {
    super(entity, context);
  }

  @Override
  protected @NotNull FtpFileService createService() {
    return new FtpFileService();
  }

  @RequiredArgsConstructor
  public class FtpFile implements FsFileEntity<FtpFile> {

    private final @NotNull String id;
    private final @NotNull FTPFile file;

    @Override
    public @NotNull String getAbsolutePath() {
      return id;
    }

    @Override
    public boolean isDirectory() {
      return file.isDirectory();
    }

    @Override
    public Long getSize() {
      return file.getSize() == -1 ? null : file.getSize();
    }

    @Override
    public Long getModifiedDateTime() {
      return Optional.ofNullable(file.getTimestamp()).map(Calendar::getTimeInMillis).orElse(null);
    }

    @Override
    @SneakyThrows
    public @Nullable FtpFile getParent(boolean stub) {
      Path parent = Paths.get(id).getParent();
      if (isPathEmpty(parent)) {
        return null;
      }
      if (stub) {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setType(DIRECTORY_TYPE);
        ftpFile.setName(parent.getFileName().toString());
        return new FtpFile(fixPath(parent), ftpFile);
      } else {
        return service.getFile(fixPath(parent));
      }
    }

    @Override
    public boolean hasChildren() {
      return file.getSize() != 6;
    }

    @Override
    public BaseFileSystemEntity getEntity() {
      return entity;
    }
  }

  public class FtpFileService implements BaseFSService<FtpFile> {

    @Override
    @SneakyThrows
    public void close() {
    }

    @Override
    public @NotNull InputStream getInputStream(@NotNull String id) throws Exception {
      return entity.execute(ftpClient -> {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        if (!ftpClient.retrieveFile(id, outputStream)) {
          throw new RuntimeException("Unable to retrieve file: <" + id + "> from ftp. Msg: " + ftpClient.getReplyString());
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
      }, true);
    }

    @Override
    public void mkdir(@NotNull String id) throws Exception {
      entity.execute(ftpClient -> {
        ftpClient.makeDirectory(id);
        return null;
      }, false);
    }

    @Override
    public void put(@NotNull InputStream inputStream, @NotNull String id) throws Exception {
      entity.execute(ftpClient -> {
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        return ftpClient.storeFile(id, inputStream);
      }, true);
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName) throws Exception {
      entity.execute(ftpClient -> ftpClient.rename(oldName, newName), false);
    }

    @Override
    public FtpFile getFile(@NotNull String id) throws Exception {
      return entity.execute(ftpClient -> {
        FTPFile file = ftpClient.mlistFile(id);
        return file == null ? null : new FtpFile(id, file);
      }, false);
    }

    @Override
    @SneakyThrows
    public List<FtpFile> readChildren(@NotNull String parentId) {
      return entity.execute(ftpClient ->
          Stream.of(ftpClient.listFiles(parentId))
                .map(id -> new FtpFile(fixPath(Paths.get(parentId).resolve(id.getName())), id))
                .collect(Collectors.toList()), false);
    }

    @Override
    @SneakyThrows
    public boolean rm(@NotNull FtpFile ftpFile) {
      return entity.execute(ftpClient -> {
        if (ftpFile.isDirectory()) {
          return ftpClient.removeDirectory(ftpFile.getId());
        } else {
          return ftpClient.deleteFile(ftpFile.getId());
        }
      }, false);
    }

    @Override
    @SneakyThrows
    public void recreate() {
    }
  }
}
