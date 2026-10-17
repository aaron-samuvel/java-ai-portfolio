const form = document.querySelector('#upload-form');
const input = document.querySelector('#image-file');
const dropZone = document.querySelector('#drop-zone');
const classifyButton = document.querySelector('#classify-button');
const fileSummary = document.querySelector('#file-summary');
const uploadMessage = document.querySelector('#upload-message');
const resultPanel = document.querySelector('#result-panel');
const preview = document.querySelector('#image-preview');
const candidateList = document.querySelector('#candidate-list');
const reviewList = document.querySelector('#review-list');
const reviewMessage = document.querySelector('#review-message');
const pendingCount = document.querySelector('#pending-count');
let previewUrl;

const setMessage = (element, message, state = '') => {
  element.textContent = message;
  element.className = `message${state ? ` ${state}` : ''}`;
};

const apiError = async (response) => {
  try {
    const body = await response.json();
    return body.detail || body.message || body.error || `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
};

const selectedFile = () => input.files?.[0] ?? null;

function describeFile(file) {
  if (!file) {
    fileSummary.hidden = true;
    classifyButton.disabled = true;
    return;
  }
  const size = `${(file.size / 1024).toFixed(0)} KB`;
  fileSummary.textContent = `${file.name} · ${size}`;
  fileSummary.hidden = false;
  const valid = ['image/png', 'image/jpeg'].includes(file.type) && file.size > 0 && file.size <= 5_000_000;
  classifyButton.disabled = !valid;
  setMessage(uploadMessage, valid ? 'Ready to analyze. The preview stays in this tab.' : 'Choose a PNG or JPEG file smaller than 5 MB.', valid ? '' : 'error');
}

input.addEventListener('change', () => describeFile(selectedFile()));

for (const eventName of ['dragenter', 'dragover']) {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.add('dragging');
  });
}
for (const eventName of ['dragleave', 'drop']) {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.remove('dragging');
  });
}
dropZone.addEventListener('drop', (event) => {
  const file = event.dataTransfer?.files?.[0];
  if (!file) return;
  const transfer = new DataTransfer();
  transfer.items.add(file);
  input.files = transfer.files;
  describeFile(file);
});
dropZone.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    input.click();
  }
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const file = selectedFile();
  if (!file || classifyButton.disabled) return;
  classifyButton.disabled = true;
  classifyButton.textContent = 'Analyzing…';
  setMessage(uploadMessage, 'Checking the image and ranking candidates…');
  const data = new FormData();
  data.append('image', file);
  try {
    const response = await fetch('/api/classify', { method: 'POST', body: data });
    if (!response.ok) throw new Error(await apiError(response));
    const result = await response.json();
    renderResult(result, file);
    setMessage(uploadMessage, 'Analysis complete. Confirm or correct the result below.');
    await loadReviews();
  } catch (error) {
    setMessage(uploadMessage, error.message || 'Could not classify this image.', 'error');
  } finally {
    classifyButton.disabled = !selectedFile() || !['image/png', 'image/jpeg'].includes(selectedFile()?.type);
    classifyButton.innerHTML = 'Analyze image <span aria-hidden="true">↗</span>';
  }
});

function renderResult(result, file) {
  resultPanel.hidden = false;
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  previewUrl = URL.createObjectURL(file);
  preview.src = previewUrl;
  document.querySelector('#image-caption').textContent = file.name;
  document.querySelector('#predicted-label').textContent = result.label;
  document.querySelector('#image-dimensions').textContent = `${result.width} × ${result.height} px`;
  document.querySelector('#result-notice').textContent = result.notice;
  candidateList.replaceChildren(...result.candidates.map((candidate) => {
    const item = document.createElement('li');
    const label = document.createElement('span');
    label.className = 'candidate-label';
    label.textContent = candidate.label;
    const score = document.createElement('span');
    score.className = 'candidate-score';
    score.textContent = `${(candidate.score * 100).toFixed(0)}%`;
    const track = document.createElement('span');
    track.className = 'score-track';
    const fill = document.createElement('span');
    fill.className = 'score-fill';
    fill.style.width = `${Math.max(0, Math.min(100, candidate.score * 100))}%`;
    track.append(fill);
    item.append(label, score, track);
    return item;
  }));
  document.querySelector('#review-result-button').dataset.reviewId = result.reviewId;
}

document.querySelector('#review-result-button').addEventListener('click', () => {
  document.querySelector('#review-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
  const id = document.querySelector('#review-result-button').dataset.reviewId;
  document.querySelector(`[data-review-id="${CSS.escape(id)}"] input`)?.focus();
});

async function loadReviews() {
  reviewList.replaceChildren();
  try {
    const response = await fetch('/api/classification-reviews');
    if (!response.ok) throw new Error(await apiError(response));
    const reviews = await response.json();
    pendingCount.textContent = String(reviews.length);
    if (reviews.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'empty-state';
      const icon = document.createElement('span');
      icon.textContent = '✓';
      const text = document.createElement('p');
      text.textContent = 'No pending items. Upload an image to start a review.';
      empty.append(icon, text);
      reviewList.append(empty);
      return;
    }
    for (const review of reviews) reviewList.append(renderReview(review));
  } catch (error) {
    pendingCount.textContent = '—';
    reviewList.replaceChildren();
    setMessage(reviewMessage, error.message || 'Could not load pending reviews.', 'error');
  }
}

function renderReview(review) {
  const card = document.createElement('article');
  card.className = 'review-card';
  card.dataset.reviewId = review.id;
  const copy = document.createElement('div');
  copy.className = 'review-copy';
  const label = document.createElement('span');
  label.className = 'detail-label';
  label.textContent = 'SUGGESTED LABEL';
  const predicted = document.createElement('strong');
  predicted.textContent = review.predictedLabel;
  const dimensions = document.createElement('span');
  dimensions.className = 'muted';
  dimensions.textContent = `${review.width} × ${review.height} px · image not retained`;
  copy.append(label, predicted, dimensions);

  const actions = document.createElement('form');
  actions.className = 'review-actions';
  const corrected = document.createElement('input');
  corrected.type = 'text';
  corrected.maxLength = 80;
  corrected.required = true;
  corrected.value = review.predictedLabel;
  corrected.setAttribute('aria-label', `Confirmed or corrected label for ${review.predictedLabel}`);
  const confirm = document.createElement('button');
  confirm.type = 'submit';
  confirm.className = 'button small-button';
  confirm.textContent = 'Confirm label';
  actions.append(corrected, confirm);
  actions.addEventListener('submit', async (event) => {
    event.preventDefault();
    confirm.disabled = true;
    try {
      const response = await fetch(`/api/classification-reviews/${encodeURIComponent(review.id)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ correctedLabel: corrected.value.trim() }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      setMessage(reviewMessage, 'Human review saved. Only the final label and metadata were retained.', 'success');
      await loadReviews();
    } catch (error) {
      confirm.disabled = false;
      setMessage(reviewMessage, error.message || 'Could not save the review.', 'error');
    }
  });
  card.append(copy, actions);
  return card;
}

loadReviews();
