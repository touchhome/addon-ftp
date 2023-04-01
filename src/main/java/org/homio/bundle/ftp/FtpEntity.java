package org.homio.bundle.ftp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pivovarit.function.ThrowingFunction;
import java.io.IOException;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.Transient;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.storage.BaseFileSystemEntity;
import org.homio.bundle.api.entity.types.StorageEntity;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.ui.UISidebarChildren;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.action.UIContextMenuAction;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.homio.bundle.api.util.SecureString;

@Entity
@UISidebarChildren(icon = "fas fa-network-wired", color = "#b32317")
public class FtpEntity extends StorageEntity<FtpEntity> implements BaseFileSystemEntity<FtpEntity, FtpFileSystem> {

  public static final String PREFIX = "ftp_";

  @JsonIgnore
  @Transient
  @Getter
  private FTPClient ftpClient;

  public String getFileSystemRoot() {
    return getJsonData("fs_root", "/");
  }

  @UIField(order = 30, required = true, inlineEditWhenEmpty = true)
  public String getUrl() {
    return getJsonData("url");
  }

  public FtpEntity setUrl(String value) {
    setJsonData("url", value);
    return this;
  }

  @UIField(order = 40)
  public String getUser() {
    return getJsonData("user");
  }

  public FtpEntity setUser(String value) {
    setJsonData("user", value);
    return this;
  }

  @UIField(order = 50)
  public SecureString getPassword() {
    return getJsonSecure("pwd");
  }

  public FtpEntity setPassword(String value) {
    setJsonData("pwd", value);
    return this;
  }

  @Override
  public String getFileSystemAlias() {
    return "FTP";
  }

  @Override
  public boolean isShowInFileManager() {
    return true;
  }

  @Override
  public String getFileSystemIcon() {
    return "fas fa-network-wired";
  }

  @Override
  public String getFileSystemIconColor() {
    return "#b32317";
  }

  @Override
  public boolean requireConfigure() {
    return StringUtils.isEmpty(getUrl());
  }

  @Override
  public FtpFileSystem buildFileSystem(EntityContext entityContext) {
    return new FtpFileSystem(this);
  }

  @Override
  public long getConnectionHashCode() {
    return Objects.hash(getUrl(), getUser(), getPassword());
  }

  @Override
  public String getDefaultName() {
    if (StringUtils.isNotEmpty(getUrl())) {
      String name = getUrl();
      if (name.startsWith("ftp.")) {
        name = name.substring(4);
      }
      if (name.endsWith(".com") || name.endsWith(".org")) {
        name = name.substring(0, name.length() - 4);
      }
      return name;
    }
    return "Ftp";
  }

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  @UIContextMenuAction(value = "TEST_CONNECTION", icon = "fas fa-ethernet")
  public ActionResponseModel testConnection() {
    FTPClient ftpClient = new FTPClient();
    try {
      try {
        ftpClient.connect(getUrl());
      } catch (Exception ex) {
        return ActionResponseModel.showError("Error connect to remove url: " + ex.getMessage());
      }
      try {
        if (!ftpClient.login(getUser(), getPassword().asString())) {
          throw new RuntimeException("User or password incorrect.");
        }
      } catch (Exception ex) {
        return ActionResponseModel.showError("Error during attempt login to ftp: " + ex.getMessage());
      }
    } finally {
      try {
        ftpClient.disconnect();
      } catch (IOException ignore) {
      }
    }
    return ActionResponseModel.showSuccess("Success connect to ftp");
  }

  public <T> T execute(ThrowingFunction<FTPClient, T, Exception> handler, boolean localPassive) throws Exception {
    FTPClient ftpClient = new FTPClient();
    Exception exception;
    try {
      ftpClient.connect(getUrl());
      if (!ftpClient.login(getUser(), getPassword().asString())) {
        throw new RuntimeException(ftpClient.getReplyString());
      }
      this.ftpClient = ftpClient;
      if (localPassive) {
        ftpClient.enterLocalPassiveMode();
      }
      return handler.apply(ftpClient);
    } catch (Exception ex) {
      exception = ex;
    } finally {
      try {
        ftpClient.logout();
      } catch (Exception ignore) {
      }
      ftpClient.disconnect();
      this.ftpClient = null;
    }
    throw exception;
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {

  }
}
