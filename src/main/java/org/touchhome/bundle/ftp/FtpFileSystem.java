package org.touchhome.bundle.ftp;

import static org.apache.commons.net.ftp.FTPFile.DIRECTORY_TYPE;

import com.google.common.primitives.Bytes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.fs.FileSystemProvider;
import org.touchhome.bundle.api.fs.TreeNode;

public class FtpFileSystem implements FileSystemProvider {

  private FtpEntity entity;
  private long connectionHashCode;

  public FtpFileSystem(FtpEntity entity) {
    this.entity = entity;
  }

  @Override
  public boolean restart(boolean force) {
    try {
      if (!force && connectionHashCode == entity.getConnectionHashCode()) {
        return true;
      }
      dispose();
      getChildren("");
      entity.setStatusOnline();
      connectionHashCode = entity.getConnectionHashCode();
      return true;
    } catch (Exception ex) {
      entity.setStatusError(ex);
      return false;
    }
  }

  @Override
  public void setEntity(Object entity) {
    this.entity = (FtpEntity) entity;
    restart(false);
  }

  @Override
  @SneakyThrows
  public InputStream getEntryInputStream(@NotNull String id) {
    return entity.execute(ftpClient -> {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
      if (!ftpClient.retrieveFile(id, outputStream)) {
        throw new RuntimeException(
            "Unable to retrieve file: <" + id + "> from ftp. Msg: " + ftpClient.getReplyString());
      }
      return new ByteArrayInputStream(outputStream.toByteArray());
    }, true);
  }

  @SneakyThrows
  @Override
  public Set<TreeNode> toTreeNodes(@NotNull Set<String> ids) {
    return entity.execute(ftpClient -> {
      Set<TreeNode> fmPaths = new HashSet<>();
      for (String id : ids) {
        FTPFile ftpFile = ftpClient.mlistFile(id);
        fmPaths.add(buildTreeNode(ftpFile, ftpFile.getName()));
      }
      return fmPaths;
    }, true);
  }

  @Override
  @SneakyThrows
  public TreeNode delete(@NotNull Set<String> ids) {
    List<FTPFile> files = entity.execute(ftpClient -> {
      List<FTPFile> deletedFiles = new ArrayList<>();
      for (String id : ids) {
        if (ftpClient.deleteFile(id)) {
          deletedFiles.add(ftpFile(id));
        }
      }
      return deletedFiles;
    }, true);
    return buildRoot(files);
  }

  @Override
  @SneakyThrows
  public TreeNode create(@NotNull String parentId, @NotNull String name, boolean isDir, UploadOption uploadOption) {
    Path path = entity.execute(ftpClient -> {
      Path fullPath = Paths.get("", parentId).resolve(name);
      String targetPath = fullPath.toString();
      if (isDir) {
        ftpClient.makeDirectory(targetPath);
      } else {
        byte[] value = new byte[0];
        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
        if (!ftpClient.storeFile(targetPath, new ByteArrayInputStream(value))) {
          throw new RuntimeException(ftpClient.getReplyString());
        }
      }
      return fullPath;
    }, true);
    return buildRoot(Collections.singletonList(ftpFile(path.toString())));
  }

  @Override
  @SneakyThrows
  public TreeNode rename(@NotNull String id, @NotNull String newName, UploadOption uploadOption) {
    return entity.execute(ftpClient -> {
      FTPFile ftpFile = ftpClient.mlistFile(id);
      if (ftpFile != null) {
        String toPath = Paths.get(id).resolveSibling(newName).toString();
        if (uploadOption != UploadOption.Replace) {
          FTPFile toFile = ftpClient.mlistFile(toPath);
          if (toFile != null) {
            if (uploadOption == UploadOption.Error) {
              throw new FileAlreadyExistsException("File " + newName + " already exists");
            } else if (uploadOption == UploadOption.SkipExist) {
              return null;
            }
          }
        }
        if (ftpClient.rename(id, toPath)) {
          ftpFile.setName(toPath);
          return buildRoot(Collections.singletonList(ftpFile));
        }
      }
      throw new IllegalStateException("File '" + id + "' not found");
    }, true);
  }

  @Override
  public TreeNode copy(@NotNull Collection<TreeNode> entries, @NotNull String targetId, UploadOption uploadOption) {
    List<FTPFile> result = new ArrayList<>();
    copyEntries(entries, targetId, uploadOption, result);
    return buildRoot(result);
  }

  @SneakyThrows
  private void copyEntries(Collection<TreeNode> entries, String targetId, UploadOption uploadOption, List<FTPFile> result) {
    entity.execute(ftpClient -> {
      for (TreeNode entry : entries) {
        String path = Paths.get(targetId).resolve(entry.getName()).toString();

        FTPFile ftpFile = ftpClient.mlistFile(path);

        if (!entry.getAttributes().isDir()) {
          try (InputStream stream = entry.getInputStream()) {
            if (ftpFile != null) {
              if (uploadOption == UploadOption.Append) {
                byte[] prependContent = IOUtils.toByteArray(ftpClient.retrieveFileStream(path));
                byte[] content = Bytes.concat(prependContent, IOUtils.toByteArray(stream));
                ftpClient.appendFile(path, new ByteArrayInputStream(content));
              } else {
                ftpClient.storeFile(path, stream);
              }
            } else {
              ftpClient.storeFile(path, stream);
            }
            result.add(ftpClient.mlistFile(path));
          }
        } else {
          ftpClient.makeDirectory(path);
          FTPFile ftpFolder = ftpFile(entry.getName());
          ftpFolder.setType(DIRECTORY_TYPE);
          result.add(ftpFolder);
          copyEntries(entry.getFileSystem().getChildren(entry), path, uploadOption, result);
        }
      }
      return null;
    }, true);
  }

  @Override
  @SneakyThrows
  public Set<TreeNode> loadTreeUpToChild(@Nullable String rootPath, @NotNull String id) {
    return entity.execute(ftpClient -> {
      FTPFile ftpFile = ftpClient.mlistFile(id);
      if (ftpFile == null) {
        return null;
      }
      Set<TreeNode> rootChildren = getChildren("");
      Set<TreeNode> currentChildren = rootChildren;
      for (Path pathItem : Paths.get(id)) {
        TreeNode foundedObject =
            currentChildren.stream().filter(c -> c.getId().equals(pathItem.toString())).findAny().orElseThrow(() ->
                new IllegalStateException("Unable find object: " + pathItem));
        currentChildren = getChildren(pathItem.toString());
        foundedObject.addChildren(currentChildren);
      }
      return rootChildren;
    }, true);
  }

  @Override
  @SneakyThrows
  public @NotNull Set<TreeNode> getChildren(@NotNull String parentId) {
    return entity.execute(ftpClient -> {
      FTPFile[] ftpFiles = ftpClient.listFiles(parentId);
      return Stream.of(ftpFiles)
          .map(ftpFile -> buildTreeNode(ftpFile, Paths.get(parentId).resolve(ftpFile.getName()).toString()))
          .collect(Collectors.toSet());
    }, true);
  }

  @Override
  @SneakyThrows
  public Set<TreeNode> getChildrenRecursively(@NotNull String parentId) {
    return entity.execute(ftpClient -> {
      TreeNode root = new TreeNode();
      buildTreeNodeRecursively(parentId, ftpClient, root);
      return root.getChildren();
    }, true);
  }

  private void buildTreeNodeRecursively(String parentId, FTPClient ftpClient, TreeNode parent) throws IOException {
    for (FTPFile ftpFile : ftpClient.listFiles(parentId)) {
      String id = Paths.get(parentId).resolve(ftpFile.getName()).toString();
      TreeNode child = parent.addChild(buildTreeNode(ftpFile, id));
      buildTreeNodeRecursively(id, ftpClient, child);
    }
  }

  @Override
  public long getTotalSpace() {
    return -1;
  }

  @Override
  public long getUsedSpace() {
    return -1;
  }

  private TreeNode buildTreeNode(FTPFile ftpFile, String id) {
    id = id.replaceAll("\\\\", "/");
    if (id.startsWith("/")) {
      id = id.substring(1);
    }
    String name = Paths.get(ftpFile.getName()).getFileName().toString();
    long timestamp = ftpFile.getTimestamp() == null ? 0 : ftpFile.getTimestamp().getTimeInMillis();
    return new TreeNode(ftpFile.isDirectory(), false, name, id,
        ftpFile.getSize(), timestamp, this, null);
  }

  private FTPFile ftpFile(String path) {
    FTPFile ftpFile = new FTPFile();
    ftpFile.setName(path);
    return ftpFile;
  }

  private TreeNode buildRoot(List<FTPFile> result) {
    Path root = Paths.get(entity.getFileSystemRoot());
    TreeNode rootPath = this.buildTreeNode(ftpFile(""), "");
    // ftpFile.getName() return FQN
    for (FTPFile ftpFile : result) {
      Path pathCursor = root;
      TreeNode cursor = rootPath;

      //build parent directories
      for (Path pathItem : root.relativize(Paths.get(ftpFile.getName()).getParent())) {
        pathCursor = pathCursor.resolve(pathItem);
        FTPFile folder = ftpFile(pathCursor.getFileName().toString());
        folder.setType(DIRECTORY_TYPE);
        cursor = cursor.addChild(buildTreeNode(folder, pathCursor.toString()));
      }
      cursor.addChild(buildTreeNode(ftpFile, ftpFile.getName()));
    }
    return rootPath;
  }
}
