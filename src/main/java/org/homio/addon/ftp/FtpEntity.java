package org.homio.addon.ftp;

import com.pivovarit.function.ThrowingFunction;
import jakarta.persistence.Entity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.time.Duration;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.homio.api.EntityContext;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.types.StorageEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;

@Entity
@UISidebarChildren(icon = "fas fa-network-wired", color = "#b32317")
public class FtpEntity extends StorageEntity<FtpEntity> implements BaseFileSystemEntity<FtpEntity, FtpFileSystem> {

  public static final String PREFIX = "ftp_";

  public @NotNull String getFileSystemRoot() {
    return getJsonData("fs_root", "/");
  }

  @UIField(order = 1, required = true, inlineEditWhenEmpty = true)
  @UIFieldGroup(value = "CONNECT", order = 10, borderColor = "#2782B0")
  public String getUrl() {
    return getJsonData("url");
  }

  public FtpEntity setUrl(String value) {
    setJsonData("url", value);
    return this;
  }

  @UIField(order = 2)
  @UIFieldPort(min = 0)
  @UIFieldGroup("CONNECT")
  public int getPort() {
    return getJsonData("port", FTP.DEFAULT_PORT);
  }

  public void setPort(int value) {
    setJsonData("port", value);
  }

  @UIField(order = 3)
  @UIFieldSlider(min = 0, max = 60)
  @UIFieldGroup("CONNECT")
  public int getControlKeepAliveTimeout() {
    return getJsonData("kat", 0);
  }

  public void setControlKeepAliveTimeout(int value) {
    setJsonData("kat", value);
  }

  @UIField(order = 4)
  @UIFieldSlider(min = 5, max = 60)
  @UIFieldGroup("CONNECT")
  public int getConnectTimeout() {
    return getJsonData("ct", 60);
  }

  public void setConnectTimeout(int value) {
    setJsonData("ct", value);
  }

  @UIField(order = 1)
  @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
  public String getUser() {
    return getJsonData("user");
  }

  public FtpEntity setUser(String value) {
    setJsonData("user", value);
    return this;
  }

  @UIField(order = 2)
  @UIFieldGroup("AUTH")
  public SecureString getPassword() {
    return getJsonSecure("pwd");
  }

  public FtpEntity setPassword(String value) {
    setJsonData("pwd", value);
    return this;
  }

  @UIField(order = 1, hideInView = true)
  @UIFieldGroup(value = "PROXY", order = 20, borderColor = "#8C324C")
  public Proxy.Type getProxyType() {
    return getJsonDataEnum("pt", Type.DIRECT);
  }

  public void setProxyType(Proxy.Type value) {
    setJsonDataEnum("pt", value);
  }

  @UIField(order = 2, hideInView = true)
  @UIFieldGroup("PROXY")
  public String getProxyHost() {
    return getJsonData("ph");
  }

  public void setProxyHost(String value) {
    setJsonData("ph", value);
  }

  @UIField(order = 3, hideInView = true)
  @UIFieldGroup("PROXY")
  public int getProxyPort() {
    return getJsonData("pp", 0);
  }

  public void setProxyPort(int value) {
    setJsonData("pp", value);
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
    return new FtpFileSystem(this, entityContext);
  }

  @Override
  public long getConnectionHashCode() {
    return Objects.hash(getUrl(), getUser(), getPassword());
  }

  @Override
  public boolean isShowHiddenFiles() {
    return true;
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
    FTPClient ftpClient = createFtpClient();
    try {
      try {
        ftpClient.connect(getUrl());
      } catch (Exception ex) {
        return ActionResponseModel.showError("Error connect to remote url: " + ex.getMessage());
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
    FTPClient ftpClient = createFtpClient();
    Exception exception;
    try {
      ftpClient.connect(getUrl(), getPort());
      if (!ftpClient.login(getUser(), getPassword().asString())) {
        throw new RuntimeException(ftpClient.getReplyString());
      }
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
    }
    throw exception;
  }

  @NotNull
  private FTPClient createFtpClient() {
    FTPClient ftpClient = new FTPClient();
    ftpClient.setConnectTimeout(getConnectTimeout() * 1000);
    ftpClient.setControlKeepAliveTimeout(Duration.ofSeconds(getControlKeepAliveTimeout()));
    if (getProxyType() != Type.DIRECT) {
      ftpClient.setProxy(new Proxy(getProxyType(), new InetSocketAddress(getProxyHost(), getProxyPort())));
    }
    return ftpClient;
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {

  }
}
