const resend = document.getElementById('resend-code');
const countdown = document.getElementById('resend-countdown');
if (resend && countdown) {
  let seconds = Number(resend.dataset.seconds || 0);
  const update = () => {
    resend.disabled = seconds > 0;
    countdown.textContent = seconds > 0 ? `You can resend in ${seconds} seconds.` : '';
    seconds -= 1;
  };
  update();
  const timer = setInterval(() => { update(); if(seconds < 0) clearInterval(timer); }, 1000);
}
