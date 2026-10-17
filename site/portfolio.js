const cards = [...document.querySelectorAll('.project-card')];
const search = document.querySelector('#project-search');
const filters = [...document.querySelectorAll('.filter-chip')];
const count = document.querySelector('#result-count');
const empty = document.querySelector('#empty-state');
let activeFilter = 'all';

function updateCards() {
  const query = search.value.trim().toLowerCase();
  let visible = 0;
  for (const card of cards) {
    const categoryMatches = activeFilter === 'all' || card.dataset.category === activeFilter;
    const textMatches = !query || `${card.innerText} ${card.dataset.search}`.toLowerCase().includes(query);
    const show = categoryMatches && textMatches;
    card.hidden = !show;
    if (show) visible++;
  }
  count.textContent = `${visible} ${visible === 1 ? 'project' : 'projects'}`;
  empty.hidden = visible !== 0;
}

for (const button of filters) {
  button.addEventListener('click', () => {
    activeFilter = button.dataset.filter;
    for (const candidate of filters) {
      const selected = candidate === button;
      candidate.classList.toggle('is-active', selected);
      candidate.setAttribute('aria-pressed', String(selected));
    }
    updateCards();
  });
}

search.addEventListener('input', updateCards);
document.addEventListener('keydown', (event) => {
  if (event.key === '/' && !['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
    event.preventDefault();
    search.focus();
  }
  if (event.key === 'Escape' && document.activeElement === search) {
    search.value = '';
    search.blur();
    updateCards();
  }
});
