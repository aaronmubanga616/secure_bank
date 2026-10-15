package dev.securebank.admin;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import dev.securebank.auth.AuthDtos;
import dev.securebank.users.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class StaffIT extends IntegrationTestSupport {
  @Autowired StaffService staff;
  @Autowired AccountService accounts;
  @Autowired JwtDecoder decoder;

  private User member(String role) {
    return users.saveAndFlush(
        User.staff(
            "staff-" + UUID.randomUUID() + "@example.test",
            "Staff Member",
            passwords.encode(PASSWORD),
            role));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor staffToken(
      User user, String role) {
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(token -> token.subject(user.id().toString()).claim("mfa", true))
        .authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
  }

  @Test
  void rolesAreEnforcedOnTheServerAndCustomerRecordsContainNoSecrets() throws Exception {
    var customer = customer();
    var admin = member("ADMIN");
    mvc.perform(get("/api/v1/admin/users").with(user(customer.id().toString()).roles("CUSTOMER")))
        .andExpect(status().isForbidden());
    for (String role : new String[] {"SUPPORT_AGENT", "COMPLIANCE_OFFICER", "ADMIN"}) {
      var member = role.equals("ADMIN") ? admin : member(role);
      var response =
          mvc.perform(get("/api/v1/admin/users/" + customer.id()).with(staffToken(member, role)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(response)
          .contains(customer.email())
          .doesNotContain("passwordHash", "mfaSecret", "sessionVersion");
      if (!role.equals("ADMIN"))
        mvc.perform(
                patch("/api/v1/admin/users/" + customer.id() + "/status")
                    .with(user(member.id().toString()).roles(role))
                    .contentType("application/json")
                    .content(
                        "{\"status\":\"SUSPENDED\",\"reason\":\"Investigation in progress.\"}"))
            .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/api/v1/fraud")
                .with(user(member("SUPPORT_AGENT").id().toString()).roles("SUPPORT_AGENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void suspensionRevokesSessionsAndRequiresARecordedReason() {
    var customer = customer();
    var admin = member("ADMIN");
    var session = auth.login(new AuthDtos.Login(customer.email(), PASSWORD));
    assertThatThrownBy(
            () ->
                staff.userStatus(
                    admin.id(), customer.id(), new StaffService.ChangeStatus("SUSPENDED", "")))
        .isInstanceOf(dev.securebank.common.ApiException.class);
    staff.userStatus(
        admin.id(),
        customer.id(),
        new StaffService.ChangeStatus("SUSPENDED", "Investigation in progress."));
    assertThatThrownBy(() -> decoder.decode(session.accessToken()))
        .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    assertThatThrownBy(() -> auth.refresh(session.refreshToken()))
        .isInstanceOf(dev.securebank.common.ApiException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT reason FROM staff_actions WHERE resource=?",
                String.class,
                "users/" + customer.id()))
        .isEqualTo("Investigation in progress.");
  }

  @Test
  void freezingPreservesTheJournalAndAuditHistoryIsImmutable() {
    var customer = customer();
    var account = accounts.create(customer.id(), AccountService.Type.CURRENT);
    var admin = member("ADMIN");
    staff.accountStatus(
        admin.id(),
        account.id(),
        new StaffService.ChangeStatus("FROZEN", "Customer requested a temporary freeze."));
    assertThat(accounts.get(customer.id(), account.id()).status()).isEqualTo("FROZEN");
    assertThat(accounts.get(customer.id(), account.id()).balance()).isEqualTo("0.00");
    UUID id =
        jdbc.queryForObject(
            "SELECT id FROM audit_logs WHERE action='ACCOUNT_FROZEN' AND user_id=?",
            UUID.class,
            admin.id());
    assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET result='ALTERED' WHERE id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Autowired dev.securebank.auth.Totp totp;

  @Test
  void staffOperationsRequireAnActualAuthenticatorSession() throws Exception {
    var admin = member("ADMIN");
    var passwordOnly = auth.login(new AuthDtos.Login(admin.email(), PASSWORD));
    mvc.perform(
            get("/api/v1/admin/system")
                .header("Authorization", "Bearer " + passwordOnly.accessToken()))
        .andExpect(status().isForbidden());
    var setup = auth.setupMfa(admin.id(), PASSWORD);
    var codes =
        auth.confirmMfa(
            admin.id(), totp.atStep(setup.secret(), java.time.Instant.now().getEpochSecond() / 30));
    var challenge = auth.login(new AuthDtos.Login(admin.email(), PASSWORD));
    var verified =
        auth.verifyMfa(
            new AuthDtos.MfaVerify(challenge.challenge(), codes.recoveryCodes().getFirst()));
    mvc.perform(
            get("/api/v1/admin/system").header("Authorization", "Bearer " + verified.accessToken()))
        .andExpect(status().isOk());
  }

  @Test
  void logoutRevokesARefreshThatWonTheRaceAndPendingMfaDiesOnPasswordChange() {
    var customer = customer();
    var first = auth.login(new AuthDtos.Login(customer.email(), PASSWORD));
    var rotated = auth.refresh(first.refreshToken());
    auth.logout(first.refreshToken());
    assertThatThrownBy(() -> auth.refresh(rotated.refreshToken()))
        .isInstanceOf(dev.securebank.common.ApiException.class);
    var setup = auth.setupMfa(customer.id(), PASSWORD);
    var codes =
        auth.confirmMfa(
            customer.id(),
            totp.atStep(setup.secret(), java.time.Instant.now().getEpochSecond() / 30));
    var challenge = auth.login(new AuthDtos.Login(customer.email(), PASSWORD));
    auth.changePassword(
        customer.id(), new AuthDtos.ChangePassword(PASSWORD, "Another private password!"));
    assertThatThrownBy(
            () ->
                auth.verifyMfa(
                    new AuthDtos.MfaVerify(
                        challenge.challenge(), codes.recoveryCodes().getFirst())))
        .isInstanceOf(dev.securebank.common.ApiException.class);
  }
}
