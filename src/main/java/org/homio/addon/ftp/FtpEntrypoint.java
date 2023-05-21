package org.homio.addon.ftp;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class FtpEntrypoint implements AddonEntrypoint {

  public void init() {
  }
}
