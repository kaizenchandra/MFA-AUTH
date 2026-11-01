<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
  <#if section = "header">Verify your mobile number
  <#elseif section = "form">
    <p>We will send a six-digit code to <strong>${phone!"your mobile"}</strong>. It expires in five minutes. Never share this code.</p>
    <form action="${url.loginAction}" method="post">
      <#if !phone??>
        <p>Your identity provider did not supply an email or mobile contact. Add a mobile number to verify your account.</p>
        <label for="phone">Mobile number including country calling code</label>
        <input id="phone" name="phone" type="tel" autocomplete="tel" placeholder="+14155550123" pattern="\+[1-9][0-9]{7,14}" required />
      </#if>
      <#if sent>
        <label for="code">Verification code</label>
        <input id="code" name="code" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" autofocus />
        <button type="submit" name="operation" value="verify">Verify mobile</button>
      </#if>
      <button id="resend-code" data-seconds="${resendSeconds?c}" type="submit" name="operation" value="send" formnovalidate>Send verification code</button>
      <p id="resend-countdown" role="status" aria-live="polite"></p>
      <p>You can request another code once every 60 seconds. Delivery is limited to ten messages per hour.</p>
    </form>
    <script src="${url.resourcesPath}/js/phone.js" defer></script>
  </#if>
</@layout.registrationLayout>
