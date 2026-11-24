document.addEventListener('DOMContentLoaded', () => {
  const uiContainer = document.querySelector('.chat-ui');
  const chatForm = document.getElementById('chat-form');
  const userInput = document.getElementById('user-input');
  const sendBtn = document.getElementById('send-btn');
  const chatLog = document.getElementById('chat-log');
  const landingGreeting = document.getElementById('landing-greeting');

  let currentAbortController = null;

  // ---------------------------------------------------------------------------
  // Markdown + math rendering configuration
  //   - marked: turns the streamed text buffer into HTML (lists, bold, code, …)
  //   - KaTeX:  turns $...$ / $$...$$ into rendered math
  // ---------------------------------------------------------------------------
  if (window.marked) {
    marked.setOptions({
      gfm: true,        // GitHub-flavoured: tables, task lists, strikethrough
      breaks: true,     // a single \n becomes <br> (chat-friendly)
      headerIds: false, // don't auto-generate id="..." on headings
      mangle: false     // don't obfuscate e-mail addresses
    });
  }

  const MATH_RENDER_OPTIONS = {
    delimiters: [
      { left: '$$', right: '$$', display: true },
      { left: '$', right: '$', display: false },
      { left: '\\(', right: '\\)', display: false },
      { left: '\\[', right: '\\]', display: true }
    ],
    throwOnError: false
  };

  function renderBotContent(contentElement) {
    if (!window.marked) {
      // marked not loaded yet — fall back to raw text so the user still sees output
      contentElement.textContent = contentElement.__rawBuffer || '';
      return;
    }
    // 1) markdown -> HTML
    contentElement.innerHTML = marked.parse(contentElement.__rawBuffer || '');
    // 2) walk the produced HTML and replace $...$ with KaTeX output
    if (window.renderMathInElement) {
      renderMathInElement(contentElement, MATH_RENDER_OPTIONS);
    }
  }

  // Generate a unique user ID once on page load
  let userId = Math.floor(1000000000 + Math.random() * 9000000000).toString();

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

  async function fetchStreamWithRetry(url, options = {}, retries = 3) {
    for (let i = 0; i < retries; i++) {
      try {
        const response = await fetch(url, options);
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
    contentElement.__rawBuffer = '';
    let renderScheduled = false;

    const scheduleRender = () => {
      if (renderScheduled) return;
      renderScheduled = true;
      requestAnimationFrame(() => {
        renderScheduled = false;
        renderBotContent(contentElement);
        chatLog.scrollTop = chatLog.scrollHeight;
      });
    };

    try {
      while (true) {
        if (signal.aborted) {
          throw new DOMException('Stream cancelled', 'AbortError');
        }
        const { done, value } = await reader.read();
        if (done) break;
        // accumulate into the raw text buffer; never write partial HTML during
        // streaming (avoids mid-tag invalid markup like <h1>...</h1 split across chunks)
        contentElement.__rawBuffer += decoder.decode(value, { stream: true });
        scheduleRender();
      }
      // one final pass so any trailing token is fully rendered
      scheduleRender();
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
      const response = await fetchStreamWithRetry(
        `/stream/${userId}/chat`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'text/plain'
          },
          body: prompt
        }
      );
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
