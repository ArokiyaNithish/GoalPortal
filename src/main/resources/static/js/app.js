// ============================================
// ATOMQUEST GOAL PORTAL - Global JS
// Sidebar toggle, responsive utils, chart helpers
// ============================================

document.addEventListener('DOMContentLoaded', () => {

  // ====== SIDEBAR TOGGLE (mobile) ======
  const toggleBtn = document.getElementById('sidebarToggle');
  const sidebar   = document.getElementById('sidebar');
  const overlay   = document.getElementById('sidebarOverlay');

  if (toggleBtn && sidebar) {
    toggleBtn.addEventListener('click', () => {
      sidebar.classList.toggle('open');
      overlay && overlay.classList.toggle('active');
    });
    overlay && overlay.addEventListener('click', () => {
      sidebar.classList.remove('open');
      overlay.classList.remove('active');
    });
  }

  // ====== AUTO-DISMISS ALERTS ======
  document.querySelectorAll('.alert[data-auto-dismiss]').forEach(el => {
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(-8px)';
      el.style.transition = '0.3s ease';
      setTimeout(() => el.remove(), 300);
    }, 4000);
  });

  // ====== WEIGHTAGE LIVE COUNTER ======
  const weightageInputs = document.querySelectorAll('.weightage-input');
  const weightageDisplay = document.getElementById('weightageTotal');
  const weightageBar = document.getElementById('weightageBar');

  if (weightageInputs.length && weightageDisplay) {
    const updateWeightage = () => {
      let total = 0;
      weightageInputs.forEach(inp => { total += parseFloat(inp.value) || 0; });
      weightageDisplay.textContent = total.toFixed(1) + '%';
      weightageDisplay.className = 'weightage-value ' +
        (total === 100 ? 'exact' : total > 100 ? 'over' : '');
      if (weightageBar) {
        weightageBar.style.width = Math.min(total, 100) + '%';
        weightageBar.className = 'progress-bar ' +
          (total === 100 ? 'success' : total > 100 ? 'danger' : 'primary');
      }
    };
    weightageInputs.forEach(inp => inp.addEventListener('input', updateWeightage));
    updateWeightage();
  }

  // ====== UOM TYPE TOGGLE (show/hide fields) ======
  const uomSelect = document.getElementById('uomType');
  if (uomSelect) {
    const updateUomFields = () => {
      const val = uomSelect.value;
      const numericFields  = document.getElementById('numericFields');
      const timelineFields = document.getElementById('timelineFields');
      const zeroFields     = document.getElementById('zeroFields');
      const directionField = document.getElementById('directionField');

      if (numericFields)  numericFields.style.display  = ['NUMERIC','PERCENTAGE'].includes(val) ? '' : 'none';
      if (directionField) directionField.style.display = ['NUMERIC','PERCENTAGE'].includes(val) ? '' : 'none';
      if (timelineFields) timelineFields.style.display = val === 'TIMELINE'   ? '' : 'none';
      if (zeroFields)     zeroFields.style.display     = val === 'ZERO_BASED' ? '' : 'none';
    };
    uomSelect.addEventListener('change', updateUomFields);
    updateUomFields();
  }

  // ====== CONFIRM DELETE ======
  document.querySelectorAll('[data-confirm]').forEach(el => {
    el.addEventListener('click', e => {
      if (!confirm(el.dataset.confirm)) e.preventDefault();
    });
  });

  // ====== INLINE EDIT TOGGLE (manager approval) ======
  document.querySelectorAll('.btn-inline-edit').forEach(btn => {
    btn.addEventListener('click', () => {
      const row = btn.closest('tr') || btn.closest('.goal-card');
      row.querySelectorAll('.view-mode').forEach(el => el.style.display = 'none');
      row.querySelectorAll('.edit-mode').forEach(el => el.style.display = '');
    });
  });

  document.querySelectorAll('.btn-cancel-edit').forEach(btn => {
    btn.addEventListener('click', () => {
      const row = btn.closest('tr') || btn.closest('.goal-card');
      row.querySelectorAll('.view-mode').forEach(el => el.style.display = '');
      row.querySelectorAll('.edit-mode').forEach(el => el.style.display = 'none');
    });
  });
});

// ====== CHART FACTORY HELPERS ======
function createDoughnutChart(canvasId, labels, data, colors) {
  const ctx = document.getElementById(canvasId);
  if (!ctx) return;
  return new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{ data, backgroundColor: colors, borderWidth: 2, borderColor: '#fff' }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '70%',
      plugins: {
        legend: { position: 'right', labels: { font: { size: 12 }, padding: 12 } }
      }
    }
  });
}

function createBarChart(canvasId, labels, datasets, options = {}) {
  const ctx = document.getElementById(canvasId);
  if (!ctx) return;
  return new Chart(ctx, {
    type: 'bar',
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'top' } },
      scales: {
        y: { beginAtZero: true, grid: { color: '#f3f4f6' }, ticks: { font: { size: 12 } } },
        x: { grid: { display: false }, ticks: { font: { size: 12 } } }
      },
      ...options
    }
  });
}

function createLineChart(canvasId, labels, datasets) {
  const ctx = document.getElementById(canvasId);
  if (!ctx) return;
  return new Chart(ctx, {
    type: 'line',
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'top' } },
      scales: {
        y: { beginAtZero: true, max: 100, grid: { color: '#f3f4f6' } },
        x: { grid: { display: false } }
      },
      elements: { line: { tension: 0.4 }, point: { radius: 5, hoverRadius: 7 } }
    }
  });
}
