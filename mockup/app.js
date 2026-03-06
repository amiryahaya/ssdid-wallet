// ============================================
// SSDID Mobile App Mockup — Navigation & Interactions
// ============================================

// Screen Navigation
function showScreen(screenId) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.getElementById('screen-' + screenId).classList.add('active');

  document.querySelectorAll('.nav-pill').forEach(p => {
    p.classList.toggle('active', p.dataset.screen === screenId);
  });
}

// Nav pill clicks
document.querySelectorAll('.nav-pill').forEach(pill => {
  pill.addEventListener('click', () => showScreen(pill.dataset.screen));
});

// Onboarding slide navigation
let currentSlide = 0;

function nextSlideOrScreen() {
  const slides = document.querySelectorAll('.slide');
  const dots = document.querySelectorAll('.dot');

  if (currentSlide < slides.length - 1) {
    slides[currentSlide].classList.remove('active');
    dots[currentSlide].classList.remove('active');
    currentSlide++;
    slides[currentSlide].classList.add('active');
    dots[currentSlide].classList.add('active');

    if (currentSlide === slides.length - 1) {
      document.querySelector('#screen-onboarding .btn-text').textContent = 'Get Started';
    }
  } else {
    showScreen('create-identity');
  }
}

// Algorithm selection
document.querySelectorAll('.algo-option').forEach(opt => {
  opt.addEventListener('click', () => {
    document.querySelectorAll('.algo-option').forEach(o => {
      o.classList.remove('selected');
      const check = o.querySelector('.algo-check');
      if (check) check.remove();
    });
    opt.classList.add('selected');

    if (!opt.querySelector('.algo-check')) {
      const check = document.createElement('span');
      check.className = 'algo-check';
      check.innerHTML = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M4 8L7 11L12 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';
      opt.appendChild(check);
    }

    // Show/hide level selector based on KAZ-Sign selection
    const levelSelector = document.querySelector('.level-selector');
    const levelLabel = levelSelector?.previousElementSibling;
    if (opt.dataset.algo === 'kaz-sign') {
      if (levelSelector) levelSelector.style.display = 'flex';
      if (levelLabel) levelLabel.style.display = 'block';
    } else {
      if (levelSelector) levelSelector.style.display = 'none';
      if (levelLabel) levelLabel.style.display = 'none';
    }
  });
});

// Level selection
document.querySelectorAll('.level-option').forEach(opt => {
  opt.addEventListener('click', () => {
    document.querySelectorAll('.level-option').forEach(o => o.classList.remove('selected'));
    opt.classList.add('selected');
  });
});

// Identity selection in registration
document.querySelectorAll('.id-select-option').forEach(opt => {
  opt.addEventListener('click', () => {
    document.querySelectorAll('.id-select-option').forEach(o => o.classList.remove('selected'));
    opt.classList.add('selected');
  });
});

// Toggle switches
document.querySelectorAll('.toggle').forEach(toggle => {
  toggle.addEventListener('click', () => {
    toggle.classList.toggle('active');
  });
});

// Copy button feedback
document.querySelectorAll('.copy-btn').forEach(btn => {
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const original = btn.innerHTML;
    btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M4 7L6 9L10 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';
    btn.style.color = 'var(--success)';
    setTimeout(() => {
      btn.innerHTML = original;
      btn.style.color = '';
    }, 1500);
  });
});

// Tab bar active state
document.querySelectorAll('.tab-bar .tab').forEach(tab => {
  tab.addEventListener('click', () => {
    const tabBar = tab.closest('.tab-bar');
    tabBar.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    if (!tab.classList.contains('scan-tab')) {
      tab.classList.add('active');
    }
  });
});

// Touch-like feedback on cards
document.querySelectorAll('.id-card, .cred-card, .cred-mini, .action-card, .activity-item, .settings-item').forEach(el => {
  el.addEventListener('mousedown', () => {
    el.style.transform = 'scale(0.98)';
    el.style.transition = 'transform 0.1s';
  });
  el.addEventListener('mouseup', () => {
    el.style.transform = '';
  });
  el.addEventListener('mouseleave', () => {
    el.style.transform = '';
  });
});
