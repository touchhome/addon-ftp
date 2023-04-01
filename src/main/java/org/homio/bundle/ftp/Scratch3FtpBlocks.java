package org.homio.bundle.ftp;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.storage.Scratch3BaseFileSystemExtensionBlocks;

@Getter
@Component
public class Scratch3FtpBlocks extends Scratch3BaseFileSystemExtensionBlocks<FtpEntrypoint, FtpEntity> {

  public Scratch3FtpBlocks(EntityContext entityContext, FtpEntrypoint ftpEntrypoint) {
    super("#306b75", entityContext, ftpEntrypoint, FtpEntity.class);
  }
}
