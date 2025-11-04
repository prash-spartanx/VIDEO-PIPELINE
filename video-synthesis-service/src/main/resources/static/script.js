document.addEventListener('DOMContentLoaded', () => {

    // --- CONFIGURATION ---
    const API_BASE_URL = 'http://localhost:8080/api'; // IMPORTANT: Change if your backend port is different

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

    // --- API HELPER ---
    async function apiRequest(endpoint, method, body) {
        const headers = new Headers({
            'Content-Type': 'application/json'
        });

        if (jwtToken) {
            headers.append('Authorization', `Bearer ${jwtToken}`);
        }

        const config = {
            method,
            headers,
            body: body ? JSON.stringify(body) : undefined
        };

        try {
            const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'An API error occurred');
            }
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return response.json();
            }
            return; // Handle 200 OK with no content

        } catch (error) {
            console.error('API Request Failed:', error);
            alert(`Error: ${error.message}`);
            throw error;
        }
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

        if (!role) {
            role = "USER"; // Default role
        }

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

        if (isCurrentUserAdmin()) {
            adminDashboardBtn.classList.remove('hidden');
            showAdminDashboard();
        } else {
            adminDashboardBtn.classList.add('hidden');
            showMyVideos();
        }
    }

    function toggleAuthViews() {
        loginView.classList.toggle('hidden');
        registerView.classList.toggle('hidden');
    }

    function isCurrentUserAdmin() {
        if (!currentUser || !currentUser.roles) {
            return false;
        }
        return currentUser.roles.includes('ROLE_ADMIN');
    }

    // --- CORE FEATURE LOGIC (ADMIN) ---

    /**
     * --- UPDATED LOGIC ---
     * Fetches both press releases and all existing videos to show the correct status.
     * Includes a button to trigger the PIB fetch.
     */
    async function showAdminDashboard() {
        // --- UPDATED HTML ---
        // We add the admin-header and fetch-pib-btn
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
                apiRequest('/videos/all', 'GET') // Requires ADMIN role, which is correct
            ]);

            // Create a Map for easy lookup of videos by their pressReleaseId
            const videoMap = new Map(allVideos.map(video => [video.pressReleaseId, video]));

            const prList = document.getElementById('pr-list');
            if (pressReleases.length === 0) {
                 prList.innerHTML = '<li>No press releases found in the database. Click "Fetch Latest from PIB" to scrape new releases.</li>';
                 return;
            }

            // Render the list
            prList.innerHTML = pressReleases.map(pr => {
                const existingVideo = videoMap.get(pr.id);
                let statusHtml = '';
                let buttonHtml = '';

                if (existingVideo) {
                    // A video exists for this PR, show its status
                    const status = existingVideo.status;
                    statusHtml = `<div class="status-container" id="status-${pr.id}" data-job-id="${existingVideo.jobId}" data-current-status="${status}">
                                    <span class="status status-${status}">${status}</span>
                                  </div>`;

                    if (status === 'COMPLETED') {
                        statusHtml += `<p>Video Ready: <a href="${existingVideo.videoUrl}" target="_blank" rel="noopener noreferrer">View Video</a></p>`;
                        buttonHtml = `<button class="generate-btn" disabled>Completed</button>`;
                    } else if (status === 'FAILED') {
                        statusHtml += `<p>Reason: ${existingVideo.errorMessage || 'Unknown error'}</p>`;
                        // Allow re-generation if failed
                        buttonHtml = `<button class="generate-btn">Generate Again</button>`;
                    } else if (status === 'PENDING' || status === 'PROCESSING') {
                        buttonHtml = `<button class="generate-btn" disabled>Generating...</button>`;
                    }
                } else {
                    // No video exists, show the generate button
                    statusHtml = `<div class="status-container" id="status-${pr.id}"></div>`;
                    buttonHtml = `<button class="generate-btn">Generate Video</button>`;
                }

                return `
                    <li data-pr-id="${pr.id}">
                        <div>
                            <strong>${pr.title}</strong>
                            <p>ID: ${pr.id} | Language: ${pr.language}</p>
                            ${statusHtml}
                        </div>
                        ${buttonHtml}
                    </li>
                `;
            }).join('');

            // After rendering, check for any videos stuck in PENDING/PROCESSING and start polling
            startPollingForPendingVideos();

        } catch (error) {
            contentArea.innerHTML = '<p>Failed to load press releases.</p>';
        }
    }

    /**
     * --- NEW FUNCTION ---
     * Handles the click on the "Fetch Latest from PIB" button
     */
    async function handleFetchPibClick() {
        const fetchBtn = document.getElementById('fetch-pib-btn');
        if (fetchBtn) {
            fetchBtn.disabled = true;
            fetchBtn.textContent = 'Fetching...';
        }

        try {
            // Call the correct POST endpoint to trigger the scraper
            const savedReleases = await apiRequest('/admin/fetch-latest-prs', 'POST');
            
            alert(`Successfully fetched and saved ${savedReleases.length} new press releases.`);
            
            // Refresh the dashboard to show the new data
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

    /**
     * --- NEW FUNCTION ---
     * Finds all videos that were rendered with a PENDING/PROCESSING status
     * and starts the polling process for them.
     */
    function startPollingForPendingVideos() {
        const pendingVideos = document.querySelectorAll('.status-container[data-job-id]');
        pendingVideos.forEach(container => {
            const jobId = container.dataset.jobId;
            const currentStatus = container.dataset.currentStatus;
            // Only start polling if it's not already completed/failed
            if (currentStatus === 'PENDING' || currentStatus === 'PROCESSING') {
                pollVideoStatus(jobId, container);
            }
        });
    }

    // --- CORE FEATURE LOGIC (USER VIDEOS with Search/Filter) ---

    function renderVideoList(videosToRender) {
        const videoListContainer = document.getElementById('video-list-container');
        if (videosToRender.length === 0) {
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


    async function handleGenerateClick(button) {
        button.disabled = true;
        button.textContent = 'Generating...';

        const pressReleaseId = button.closest('li').dataset.prId;
        const statusContainer = document.getElementById(`status-${pressReleaseId}`);

        try {
            const response = await apiRequest(`/admin/generate?pressReleaseId=${pressReleaseId}`, 'POST');
            statusContainer.innerHTML = `<span class="status status-PENDING">PENDING (Job ID: ${response.jobId})</span>`;

            allUserVideos = []; // Invalidate video cache

            pollVideoStatus(response.jobId, statusContainer);
        } catch (error) {
            statusContainer.innerHTML = `<span class="status status-FAILED">Submission Failed</span>`;
            button.disabled = false;
            button.textContent = 'Generate Video';
        }
    }

    /**
     * --- NEW FUNCTION ---
     * Handles all clicks inside the contentArea and delegates to the correct function.
     */
    function handleContentAreaClick(e) {
        if (e.target.classList.contains('generate-btn')) {
            handleGenerateClick(e.target);
        }
        if (e.target.id === 'fetch-pib-btn') {
            handleFetchPibClick();
        }
    }

    function pollVideoStatus(jobId, element) {
        if (element.dataset.polling === 'true') {
            return;
        }
        element.dataset.polling = 'true';

        const intervalId = setInterval(async () => {
            try {
                const statusResponse = await apiRequest(`/admin/video/status/${jobId}`, 'GET');
                const status = statusResponse.status;

                element.innerHTML = `<span class="status status-${status}">${status}</span>`;

                if (status === 'COMPLETED' || status === 'FAILED') {
                    clearInterval(intervalId);
                    element.dataset.polling = 'false'; 

                    // Refresh the dashboard to show final state (e.g., "View Video" link)
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
    
    // --- UPDATED LISTENER ---
    // This one listener now handles all clicks inside the content area
    contentArea.addEventListener('click', handleContentAreaClick);

    // --- START ---
    initializeApp();
});