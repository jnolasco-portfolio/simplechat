document.addEventListener('DOMContentLoaded', () => {
  const uiContainer = document.querySelector('.chat-ui');
  const chatForm = document.getElementById('chat-form');
  const userInput = document.getElementById('user-input');
  const sendBtn = document.getElementById('send-btn');
  const chatLog = document.getElementById('chat-log');
  const landingGreeting = document.getElementById('landing-greeting');

  let currentAbortController = null;

  userInput.addEventListener('input', () => {
    if (chatForm.classList.contains('waiting-on-response')) return;

    const textValue = userInput.value.trim();
    if (textValue.length > 0) {
      sendBtn.removeAttribute('disabled');
    } else {
      sendBtn.setAttribute('disabled', 'true');
    }
  });

  chatForm.addEventListener('submit', (e) => {
    e.preventDefault();

    if (chatForm.classList.contains('waiting-on-response')) {
      handleCancelGeneration();
      return;
    }

    const stringPayload = userInput.value.trim();
    if (!stringPayload) return;

    if (uiContainer.classList.contains('initial-landing')) {
      if (landingGreeting) landingGreeting.remove();
      chatLog.style.display = 'flex';
      uiContainer.classList.remove('initial-landing');
    }

    appendMessageNode(stringPayload, 'user');

    userInput.value = '';
    userInput.setAttribute('disabled', 'true');
    chatForm.classList.add('waiting-on-response');

    const loadingIndicator = appendLoadingSkeletonNode();
    currentAbortController = new AbortController();

    simulateBackEndWorkerReply(stringPayload, loadingIndicator, currentAbortController.signal);
  });

  function appendMessageNode(text, partyType) {
    const messageBlock = document.createElement('div');
    messageBlock.classList.add('message-block', `layout-${partyType}`);

    const bubbleElement = document.createElement('div');
    bubbleElement.classList.add('msg', `msg-${partyType}`);
    bubbleElement.textContent = text;

    messageBlock.appendChild(bubbleElement);
    chatLog.appendChild(messageBlock);
    chatLog.scrollTop = chatLog.scrollHeight;
  }

  function appendLoadingSkeletonNode() {
    const messageBlock = document.createElement('div');
    messageBlock.classList.add('message-block', 'layout-bot', 'skeleton-loading-bubble');

    messageBlock.innerHTML = `
            <div class="msg msg-bot">
                <div class="skeleton-line"></div>
                <div class="skeleton-line short"></div>
            </div>
        `;

    chatLog.appendChild(messageBlock);
    chatLog.scrollTop = chatLog.scrollHeight;
    return messageBlock;
  }

  function handleCancelGeneration() {
    if (currentAbortController) {
      currentAbortController.abort();
    }

    const activeSkeleton = document.querySelector('.skeleton-loading-bubble');
    if (activeSkeleton) activeSkeleton.remove();

    appendMessageNode('Generation cancelled by the user.', 'bot');
    resetInputCapsuleToReady();
  }

  function resetInputCapsuleToReady() {
    chatForm.classList.remove('waiting-on-response');
    userInput.removeAttribute('disabled');
    sendBtn.setAttribute('disabled', 'true');
    userInput.focus();
  }

  async function simulateBackEndWorkerReply(prompt, loadingNode, signal) {
    try {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(resolve, 2000);
        signal.addEventListener('abort', () => {
          clearTimeout(timer);
          reject(new DOMException('Aborted', 'AbortError'));
        });
      });

      loadingNode.remove();
      appendMessageNode(`Received. Your query: "${prompt}" was processed successfully on the local server.`, 'bot');
      resetInputCapsuleToReady();

    } catch (error) {
      if (error.name !== 'AbortError') {
        loadingNode.remove();
        appendMessageNode('Error connecting to the service assistant.', 'bot');
        resetInputCapsuleToReady();
      }
    }
  }
});