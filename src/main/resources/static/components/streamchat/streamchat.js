document.addEventListener('DOMContentLoaded', () => {
  const uiContainer = document.querySelector('.gemini-ui-engine');
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

    // Use real streaming from backend
    streamBackEndReply(stringPayload, loadingIndicator, currentAbortController.signal);
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
    return messageBlock;
  }

  function appendBotStreamingNode() {
    const messageBlock = document.createElement('div');
    messageBlock.classList.add('message-block', 'layout-bot');

    const bubbleElement = document.createElement('div');
    bubbleElement.classList.add('msg', 'msg-bot');
    bubbleElement.innerHTML = '';

    messageBlock.appendChild(bubbleElement);
    chatLog.appendChild(messageBlock);
    chatLog.scrollTop = chatLog.scrollHeight;
    return bubbleElement;
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

    appendMessageNode('Generation cancelled by user.', 'bot');
    resetInputCapsuleToReady();
  }

  function resetInputCapsuleToReady() {
    chatForm.classList.remove('waiting-on-response');
    userInput.removeAttribute('disabled');
    sendBtn.setAttribute('disabled', 'true');
    userInput.focus();
  }

  async function fetchStreamWithRetry(url, retries = 3) {
    for (let i = 0; i < retries; i++) {
      try {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        return response;
      } catch (e) {
        console.error(`Attempt ${i + 1} failed: ${e.message}`);
        if (i === retries - 1) throw e;
        await new Promise(resolve => setTimeout(resolve, 1000)); // Wait 1 second before retrying
      }
    }
  }

  async function processStream(reader, contentElement, signal) {
    const decoder = new TextDecoder('utf-8');
    try {
      while (true) {
        if (signal.aborted) {
          throw new DOMException('Stream cancelled', 'AbortError');
        }
        const { done, value } = await reader.read();
        if (done) break;
        contentElement.innerHTML += decoder.decode(value, { stream: true });
        chatLog.scrollTop = chatLog.scrollHeight;
      }
    } catch (error) {
      if (error.name !== 'AbortError') {
        console.error('Error processing stream:', error);
        contentElement.innerHTML += '<br>[Error: Stream interrupted. Please try again.]';
      }
      throw error;
    }
  }

  async function streamBackEndReply(prompt, loadingNode, signal) {
    try {
      const response = await fetchStreamWithRetry('/stream?message=' + encodeURIComponent(prompt));
      const reader = response.body.getReader();
      
      // Remove loading skeleton
      loadingNode.remove();
      
      // Create bot message element for streaming content
      const contentElement = appendBotStreamingNode();
      
      // Process the stream
      await processStream(reader, contentElement, signal);
      
      resetInputCapsuleToReady();
    } catch (error) {
      if (error.name === 'AbortError') {
        // User cancelled, already handled
        return;
      }
      
      console.error('Error fetching chatbot response:', error);
      loadingNode.remove();
      appendMessageNode('Error connecting to AI service. Please try again.', 'bot');
      resetInputCapsuleToReady();
    }
  }
});
