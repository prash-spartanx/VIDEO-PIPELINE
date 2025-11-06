document.addEventListener('DOMContentLoaded', () => {

    // --- CONFIGURATION ---
    const API_BASE_URL = 'http://localhost:8080/api'; // IMPORTANT: Change if your backend port is different

    const SUPPORTED_LANGUAGES = [
      { label: 'English', value: 'english' },
      { label: 'Hindi', value: 'hindi' },
      { label: 'Spanish', value: 'spanish' },
      { label: 'French', value: 'french' },
      { label: 'German', value: 'german' },
      { label: 'Italian', value: 'italian' },
      { label: 'Portuguese', value: 'portuguese' },
      { label: 'Russian', value: 'russian' },
      { label: 'Japanese', value: 'japanese' },
      { label: 'Korean', value: 'korean' },
      { label: 'Chinese', value: 'chinese' },
      { label: 'Arabic', value: 'arabic' },
    ];

    // --- UI ELEMENTS ---
    const authContainer = document.getElementById('auth-container');
    const mainApp = document.getElementById('main-app');
    const loginView = document.getElementById('login-view');
    const registerView = document.getElementById('register-view');
    const contentArea = document.getElementById('content-area');
    const adminDashboardBtn = document.getElementById('admin-dashboard-btn');
    const welcomeMessage = document.getElementById('welcome-message');

    // --- FORMS & BUTTONS ---
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const logoutBtn = document.getElementById('logout-btn');
    const showRegisterLink = document.getElementById('show-register');
    const showLoginLink = document.getElementById('show-login');

    // --- STATE ---
    let jwtToken = null;
    let currentUser = null; // Will now store { username, roles }
    let allUserVideos = []; // Cache for 'My Videos' page

    // --- API HELPERS ---
    async function apiRequest(endpoint, method, body) {
        const headers = new Headers({ 'Content-Type': 'application/json' });
        if (jwtToken) headers.append('Authorization', `Bearer ${jwtToken}`);

        const config = { method, headers, body: body ? JSON.stringify(body) : undefined };

        try {
            const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
            if (!response.ok) {
                let message = 'An API error occurred';
                try {
                  const errorData = await response.json();
                  message = errorData.message || errorData.error || message;
                } catch {}
                throw new Error(message);
            }
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return response.json();
            }
            return response.text();
        } catch (error) {
            console.error('API Request Failed:', error);
            alert(`Error: ${error.message}`);
            throw error;
        }
    }

    async function apiRequestText(endpoint, method, body) {
        const headers = new Headers({ 'Content-Type': 'application/json' });
        if (jwtToken) headers.append('Authorization', `Bearer ${jwtToken}`);
        const res = await fetch(`${API_BASE_URL}${endpoint}`, {
            method,
            headers,
            body: body ? JSON.stringify(body) : undefined
        });
        if (!res.ok) {
            const txt = await res.text();
            throw new Error(txt || 'API error');
        }
        return res.text();
    }

    // --- AUTHENTICATION LOGIC ---
    async function handleLogin(e) {
        e.preventDefault();
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        try {
            const data = await apiRequest('/auth/login', 'POST', { username, password });
            jwtToken = data.token;

            currentUser = {
                username: data.username,
                roles: data.roles || []
            };

            localStorage.setItem('jwtToken', jwtToken);
            localStorage.setItem('currentUser', JSON.stringify(currentUser));

            initializeApp();
        } catch (error) {
            console.error('Login failed');
        }
    }

    async function handleRegister(e) {
        e.preventDefault();
        const username = document.getElementById('register-username').value;
        const email = document.getElementById('register-email').value;
        const password = document.getElementById('register-password').value;
        let role = document.getElementById('register-role').value;
        if (!role) role = "USER";

        try {
            await apiRequest('/auth/register', 'POST', { username, email, password, role });
            alert('Registration successful! Please log in.');
            toggleAuthViews();
        } catch (error) {
            console.error('Registration failed');
        }
    }

    function handleLogout() {
        jwtToken = null;
        currentUser = null;
        allUserVideos = [];
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('currentUser');
        showAuthView();
    }

    // --- VIEW RENDERING ---
    function showAuthView() {
        authContainer.classList.remove('hidden');
        mainApp.classList.add('hidden');
    }

    function showMainAppView() {
        authContainer.classList.add('hidden');
        mainApp.classList.remove('hidden');
        welcomeMessage.textContent = `Welcome, ${currentUser.username}`;

        // NEW: render hero based on role
        renderHeroCTA();

        if (isCurrentUserAdmin()) {
            adminDashboardBtn.classList.remove('hidden');
            // showAdminDashboard(); // <-- comment this to keep hero front-and-center
        } else {
            adminDashboardBtn.classList.add('hidden');
            // showMyVideos(); // optional: keep commented to stay on hero
        }
    }

    function toggleAuthViews() {
        loginView.classList.toggle('hidden');
        registerView.classList.toggle('hidden');
    }

    function isCurrentUserAdmin() {
        if (!currentUser || !currentUser.roles) return false;
        return currentUser.roles.includes('ROLE_ADMIN');
    }

    // --- NEW: render the hero based on role ---
    function renderHeroCTA() {
      const hero = document.getElementById('hero-cta');
      if (!hero) return;

      const isAdmin = isCurrentUserAdmin();

      if (isAdmin) {
        hero.innerHTML = `
          <h1 class="h1">Operate <em>TenjikuAi</em> with Ease</h1>
          <p class="muted">Pull the latest PIB releases and start generating high-quality videos in minutes.</p>
          <div class="cta-row">
            <button id="fetch-pib-cta" class="cta">Fetch Latest Press Releases</button>
            <button id="open-admin" class="cta secondary">Open Admin Dashboard</button>
          </div>
        `;
      } else {
        hero.innerHTML = `
          <h1 class="h1">Glow <em>Naturally</em> Feel <br/> Proud Every Day</h1>
          <p class="muted">Discover India’s latest government updates—clear, credible, and crafted for everyone.</p>
          <div class="search-row">
            <input id="hero-search-text" type="text" placeholder="Search press releases or videos…" />
            <input id="hero-search-date" type="date" />
            <button id="hero-search-btn">Search</button>
          </div>
        `;
      }

      // Wire up CTA actions
      const fetchBtn = document.getElementById('fetch-pib-cta');
      if (fetchBtn) fetchBtn.addEventListener('click', async () => {
        // reuse your dashboard fetch logic
        await showAdminDashboard();
        const dashBtn = document.getElementById('fetch-pib-btn');
        if (dashBtn) dashBtn.click(); // triggers the same flow
      });

      const openAdmin = document.getElementById('open-admin');
      if (openAdmin) openAdmin.addEventListener('click', showAdminDashboard);

      const searchBtn = document.getElementById('hero-search-btn');
      if (searchBtn) searchBtn.addEventListener('click', async () => {
        // For now, route to Public Gallery; you can extend this to pass filters
        await showPublicGallery();
        // optional: you can read #hero-search-text and #hero-search-date and filter client-side
      });
    }

    // --- CORE FEATURE LOGIC (ADMIN) ---
    async function showAdminDashboard() {
        contentArea.innerHTML = `
            <div class="admin-header">
                <h2>Admin Dashboard: Latest Press Releases</h2>
                <button id="fetch-pib-btn">Fetch Latest from PIB</button>
            </div>
            <ul id="pr-list"><li>Loading...</li></ul>
        `;
        
        try {
            // Fetch both PRs and all videos in the system
            const [pressReleases, allVideos] = await Promise.all([
                apiRequest('/admin/latest-prs', 'GET'),
                apiRequest('/videos/all', 'GET') // Requires ADMIN role
            ]);

            const videoMap = new Map(allVideos.map(video => [video.pressReleaseId, video]));
            const prList = document.getElementById('pr-list');

            if (!pressReleases || pressReleases.length === 0) {
                 prList.innerHTML = '<li>No press releases found in the database. Click "Fetch Latest from PIB" to scrape new releases.</li>';
                 return;
            }

            prList.innerHTML = pressReleases.map(pr => {
                const existingVideo = videoMap.get(pr.id);
                let statusHtml = '';
                let buttonHtml = '';
                let videoIdHtml = '';

                if (existingVideo) {
                    const status = existingVideo.status;
                    statusHtml = `<div class="status-container" id="status-${pr.id}" data-job-id="${existingVideo.jobId || ''}" data-current-status="${status}">
                                    <span class="status status-${status}">${status}</span>
                                  </div>`;

                    if (status === 'COMPLETED') {
                        statusHtml += `<p>Video Ready: <a href="${existingVideo.videoUrl}" target="_blank" rel="noopener noreferrer">View Video</a></p>`;
                        videoIdHtml = `<p><strong>Video ID:</strong> ${existingVideo.id}</p>`;
                        buttonHtml = `<button class="generate-btn" disabled>Completed</button>`;
                    } else if (status === 'FAILED') {
                        statusHtml += `<p>Reason: ${existingVideo.errorMessage || 'Unknown error'}</p>`;
                        buttonHtml = `<button class="generate-btn">Generate Again</button>`;
                    } else if (status === 'PENDING' || status === 'PROCESSING') {
                        buttonHtml = `<button class="generate-btn" disabled>Generating...</button>`;
                    }
                } else {
                    statusHtml = `<div class="status-container" id="status-${pr.id}"></div>`;
                    buttonHtml = `<button class="generate-btn">Generate Video</button>`;
                }

                const langOptions = SUPPORTED_LANGUAGES.map(l => 
                  `<option value="${l.value}" ${(pr.language && pr.language.toLowerCase() === l.value) ? 'selected' : ''}>${l.label}</option>`
                ).join('');

                // NOTE: data-video-id is set only when we have existingVideo
                return `
                    <li data-pr-id="${pr.id}" ${existingVideo ? `data-video-id="${existingVideo.id}"` : ''}>
                        <div>
                            <strong>${pr.title}</strong>
                            <p>ID: ${pr.id} | Default Language: ${pr.language || 'en'}</p>
                            <div style="display:flex; gap:.5rem; align-items:center;">
                                <label>Language:</label>
                                <select class="language-select">
                                  ${langOptions}
                                </select>
                            </div>
                            ${statusHtml}
                            ${videoIdHtml}
                        </div>
                        <div style="display:flex; flex-direction:column; gap:.5rem; align-items:flex-end;">
                            ${buttonHtml}
                            <button class="improvise-btn">Improvise</button>
                            <button class="publish-btn" ${existingVideo && existingVideo.status === 'COMPLETED' ? '' : 'disabled'}>Publish</button>
                        </div>
                    </li>
                `;
            }).join('');

            startPollingForPendingVideos();

        } catch (error) {
            console.error(error);
            contentArea.innerHTML = '<p>Failed to load press releases.</p>';
        }
    }

    async function handleFetchPibClick() {
        const fetchBtn = document.getElementById('fetch-pib-btn');
        if (fetchBtn) {
            fetchBtn.disabled = true;
            fetchBtn.textContent = 'Fetching...';
        }

        try {
            const savedReleases = await apiRequest('/admin/fetch-latest-prs', 'POST');
            const count = Array.isArray(savedReleases) ? savedReleases.length : 0;
            alert(`Successfully fetched and saved ${count} press releases.`);
            await showAdminDashboard();
        } catch (error) {
            console.error('Failed to fetch from PIB:', error);
            alert('An error occurred while fetching from PIB.');
            if (fetchBtn) {
                fetchBtn.disabled = false;
                fetchBtn.textContent = 'Fetch Latest from PIB';
            }
        }
    }

    function startPollingForPendingVideos() {
        const pendingVideos = document.querySelectorAll('.status-container[data-job-id]');
        pendingVideos.forEach(container => {
            const jobId = container.dataset.jobId;
            const currentStatus = container.dataset.currentStatus;
            if (jobId && (currentStatus === 'PENDING' || currentStatus === 'PROCESSING')) {
                pollVideoStatus(jobId, container);
            }
        });
    }

    // --- CORE FEATURE LOGIC (USER VIDEOS with Search/Filter) ---
    function renderVideoList(videosToRender) {
        const videoListContainer = document.getElementById('video-list-container');
        if (!videosToRender || videosToRender.length === 0) {
            videoListContainer.innerHTML = '<ul><li>No videos match the current filters.</li></ul>';
            return;
        }
        videoListContainer.innerHTML = '<ul>' + videosToRender.map(v => `
            <li>
                <div>
                    <strong>Video ID: ${v.id}</strong> (from Press Release ID: ${v.pressReleaseId})
                    <p>Published: ${v.publishedAt ? new Date(v.publishedAt).toLocaleDateString() : 'Not published'}</p>
                    <p>Video URL: ${v.videoUrl ? `<a href="${v.videoUrl}" target="_blank" rel="noopener noreferrer">View Video</a>` : 'Not available'}</p>
                    ${v.errorMessage ? `<p style="color:red;">Error: ${v.errorMessage}</p>` : ''}
                </div>
                <span class="status status-${v.status}">${v.status}</span>
            </li>
        `).join('') + '</ul>';
    }

    function filterAndRenderVideos() {
        const searchTerm = document.getElementById('search-term').value.toLowerCase();
        const publishedDate = document.getElementById('published-date').value;

        let filteredVideos = allUserVideos.filter(video => {
            const matchesSearch = searchTerm === '' ||
                video.id.toString().includes(searchTerm) ||
                video.pressReleaseId.toString().includes(searchTerm);

            const matchesDate = publishedDate === '' ||
                (video.publishedAt && video.publishedAt.startsWith(publishedDate));

            return matchesSearch && matchesDate;
        });

        renderVideoList(filteredVideos);
    }

    function renderVideoControlsAndList() {
        contentArea.innerHTML = `
            <h2>My Generated Videos</h2>
            <div id="video-controls">
                <input type="text" id="search-term" placeholder="Search by Video or PR ID...">
                <input type="date" id="published-date">
                <button id="reset-filters">Reset</button>
            </div>
            <div id="video-list-container"></div>
        `;

        renderVideoList(allUserVideos);

        document.getElementById('search-term').addEventListener('input', filterAndRenderVideos);
        document.getElementById('published-date').addEventListener('input', filterAndRenderVideos);
        document.getElementById('reset-filters').addEventListener('click', () => {
            document.getElementById('search-term').value = '';
            document.getElementById('published-date').value = '';
            filterAndRenderVideos();
        });
    }

    async function showMyVideos() {
        contentArea.innerHTML = '<h2>My Generated Videos</h2><p>Loading videos...</p>';

        if (allUserVideos.length === 0) {
            try {
                allUserVideos = await apiRequest('/videos/my-videos', 'GET');
            } catch (error) {
                 contentArea.innerHTML = '<p>Failed to load your videos.</p>';
                 return;
            }
        }
        renderVideoControlsAndList();
    }

    async function handleGenerateClick(button, scriptOverride = null) {
      button.disabled = true;
      button.textContent = 'Generating...';

      const li = button.closest('li');
      const pressReleaseId = li.dataset.prId;
      const statusContainer = document.getElementById(`status-${pressReleaseId}`);
      const langSelect = li.querySelector('.language-select');
      const lang = langSelect ? langSelect.value : null;

      try {
          const params = new URLSearchParams();
          params.set('pressReleaseId', pressReleaseId);
          if (lang) params.set('language', lang);
          if (scriptOverride) params.set('scriptOverride', scriptOverride);

          const endpoint = `/admin/generate?${params.toString()}`;
          const response = await apiRequest(endpoint, 'POST');

          statusContainer.innerHTML = `<span class="status status-PENDING">PENDING (Job ID: ${response.jobId || '...'})</span>`;
          allUserVideos = []; // invalidate cache
          if (response.jobId) {
            statusContainer.dataset.jobId = response.jobId;
            statusContainer.dataset.currentStatus = 'PENDING';
            pollVideoStatus(response.jobId, statusContainer);
          } else {
            await showAdminDashboard();
          }
      } catch (error) {
          statusContainer.innerHTML = `<span class="status status-FAILED">Submission Failed</span>`;
          button.disabled = false;
          button.textContent = 'Generate Video';
      }
    }

    async function handleImproviseClick(button) {
      const li = button.closest('li');
      const pressReleaseId = li.dataset.prId;
      const lang = li.querySelector('.language-select')?.value || 'english';

      const styleHints = prompt('Style hints (tone/pace/energy, optional):', 'concise, formal, neutral tone');
      if (styleHints === null) return;

      try {
        const params = new URLSearchParams();
        params.set('pressReleaseId', pressReleaseId);
        if (lang) params.set('language', lang);

        const scriptText = await apiRequestText(`/admin/improvise?${params.toString()}`, 'POST', { styleHints });

        const edited = prompt('Review/EDIT the improved narration (will be used as voiceover):', scriptText);
        if (edited === null || !edited.trim()) return;

        const generateBtn = li.querySelector('.generate-btn') || button;
        await handleGenerateClick(generateBtn, edited.trim());

      } catch (err) {
        alert('Improvise failed. Check logs.');
        console.error(err);
      }
    }

    async function handlePublishClick(button) {
      const li = button.closest('li');
      // Prefer the embedded video ID if present (when COMPLETED)
      const embeddedId = li.dataset.videoId && li.dataset.videoId.trim();
      const videoId = embeddedId || prompt('Enter the Completed Video ID to publish:');
      if (!videoId) return;

      try {
        // Empty body is OK; backend will store PUBLISHED with null platform/publishedUrl
        const resp = await fetch(`${API_BASE_URL}/admin/publish?videoId=${encodeURIComponent(videoId)}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...(jwtToken ? { 'Authorization': `Bearer ${jwtToken}` } : {}) },
          body: JSON.stringify({})
        });
        if (!resp.ok) {
          const err = await resp.text();
          throw new Error(err || 'Publish failed');
        }
        alert('Published! It will now appear in Public Gallery.');
        await showAdminDashboard();
      } catch (e) {
        console.error(e);
        alert('Publish failed. Double-check the Video ID is the COMPLETED video’s ID.');
      }
    }

    async function showPublicGallery() {
      contentArea.innerHTML = '<h2>Public Gallery</h2><p>Loading...</p>';
      try {
        const published = await apiRequest('/public/videos', 'GET');
        if (!published || published.length === 0) {
          contentArea.innerHTML = '<h2>Public Gallery</h2><p>No published videos yet.</p>';
          return;
        }
        const items = published.map(v => `
          <li>
            <div>
              <strong>PR ID: ${v.pressReleaseId}</strong>
              <p>Published: ${v.publishedAt ? new Date(v.publishedAt).toLocaleString() : '-'}</p>
              <p><a href="${v.publishedUrl || v.videoUrl}" target="_blank" rel="noopener noreferrer">Watch</a></p>
              ${v.platform ? `<p>Platform: ${v.platform}</p>` : ''}
            </div>
            <span class="status status-${v.status}">${v.status}</span>
          </li>
        `).join('');
        contentArea.innerHTML = `<h2>Public Gallery</h2><ul>${items}</ul>`;
      } catch (e) {
        console.error(e);
        contentArea.innerHTML = '<h2>Public Gallery</h2><p>Failed to load published videos.</p>';
      }
    }

    // --- EVENT DELEGATION FOR CONTENT AREA ---
    function handleContentAreaClick(e) {
        if (e.target.classList.contains('generate-btn')) {
            handleGenerateClick(e.target);
        }
        if (e.target.id === 'fetch-pib-btn') {
            handleFetchPibClick();
        }
        if (e.target.classList.contains('improvise-btn')) {
            handleImproviseClick(e.target);
        }
        if (e.target.classList.contains('publish-btn')) {
            handlePublishClick(e.target);
        }
    }

    // --- POLLING ---
    function pollVideoStatus(jobId, element) {
        if (element.dataset.polling === 'true') return;
        element.dataset.polling = 'true';

        const intervalId = setInterval(async () => {
            try {
                const statusResponse = await apiRequest(`/admin/video/status/${jobId}`, 'GET');
                const status = statusResponse.status;

                element.innerHTML = `<span class="status status-${status}">${status}</span>`;

                if (status === 'COMPLETED' || status === 'FAILED') {
                    clearInterval(intervalId);
                    element.dataset.polling = 'false'; 
                    showAdminDashboard();
                }
            } catch (error) {
                console.error(`Polling failed for job ${jobId}`, error);
                element.innerHTML = `<span class="status status-FAILED">Polling Error</span>`;
                clearInterval(intervalId);
                element.dataset.polling = 'false';
            }
        }, 5000);
    }

    // --- INITIALIZATION ---
    function initializeApp() {
        jwtToken = localStorage.getItem('jwtToken');
        currentUser = JSON.parse(localStorage.getItem('currentUser'));

        if (jwtToken && currentUser) {
            showMainAppView();
        } else {
            showAuthView();
        }
    }

    // --- EVENT LISTENERS ---
    loginForm.addEventListener('submit', handleLogin);
    registerForm.addEventListener('submit', handleRegister);
    logoutBtn.addEventListener('click', handleLogout);
    showRegisterLink.addEventListener('click', toggleAuthViews);
    showLoginLink.addEventListener('click', toggleAuthViews);
    adminDashboardBtn.addEventListener('click', showAdminDashboard);
    document.getElementById('my-videos-btn').addEventListener('click', showMyVideos);
    document.getElementById('public-gallery-btn').addEventListener('click', showPublicGallery);
    contentArea.addEventListener('click', handleContentAreaClick);

    // --- Hook hero on Home click too ---
    document.getElementById('home-link')?.addEventListener('click', () => {
      // Re-render hero CTA and clear content panel
      renderHeroCTA();
      const area = document.getElementById('content-area');
      if (area) area.innerHTML = '';
    });

    // --- START ---
    initializeApp();
});