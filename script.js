/**
 * Force LTE Only - Premium Website JavaScript
 */

document.addEventListener('DOMContentLoaded', function() {
    initNavbar();
    initScrollAnimations();
    initPhoneAnimation();
    initCounterAnimations();
});

/**
 * Navbar Scroll Effect
 */
function initNavbar() {
    const navbar = document.querySelector('.navbar');
    const mobileMenuBtn = document.querySelector('.mobile-menu-btn');
    const navLinks = document.querySelector('.nav-links');
    
    // Scroll effect
    window.addEventListener('scroll', function() {
        if (window.scrollY > 50) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
    });
    
    // Mobile menu toggle
    if (mobileMenuBtn && navLinks) {
        mobileMenuBtn.addEventListener('click', function() {
            this.classList.toggle('active');
            navLinks.classList.toggle('active');
            document.body.style.overflow = navLinks.classList.contains('active') ? 'hidden' : '';
        });
        
        // Close menu on link click
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                mobileMenuBtn.classList.remove('active');
                navLinks.classList.remove('active');
                document.body.style.overflow = '';
            });
        });
    }
}

/**
 * Scroll Animations using Intersection Observer
 */
function initScrollAnimations() {
    const observerOptions = {
        root: null,
        rootMargin: '0px 0px -100px 0px',
        threshold: 0.1
    };
    
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry, index) => {
            if (entry.isIntersecting) {
                // Add staggered delay based on element index in its parent
                const siblings = entry.target.parentElement.querySelectorAll('.fade-in, .slide-in-left, .slide-in-right, .scale-in');
                const siblingIndex = Array.from(siblings).indexOf(entry.target);
                entry.target.style.transitionDelay = `${siblingIndex * 100}ms`;
                entry.target.classList.add('visible');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);
    
    // Observe all animated elements
    const animatedElements = document.querySelectorAll('.fade-in, .slide-in-left, .slide-in-right, .scale-in');
    animatedElements.forEach(el => observer.observe(el));
}

/**
 * Phone Mockup Animation
 */
function initPhoneAnimation() {
    const phoneContainer = document.querySelector('.phone-container');
    if (!phoneContainer) return;
    
    const speedValue = document.getElementById('speedValue');
    const speedRing = document.getElementById('speedRing');
    const rsrpVal = document.getElementById('rsrpVal');
    const rsrqVal = document.getElementById('rsrqVal');
    const sinrVal = document.getElementById('sinrVal');
    const bandVal = document.getElementById('bandVal');
    const bars = document.querySelectorAll('.signal-bars .bar');
    
    // Band options
    const bands = ['B1', 'B3', 'B5', 'B7', 'B8', 'B20', 'B28', 'B38', 'n1', 'n3', 'n7', 'n28', 'n78'];
    
    // Target speed for animation (80% of max)
    const targetSpeed = 85;
    const circumference = 2 * Math.PI * 62; // Circle radius is 62
    
    // Initial animation
    let currentSpeed = 0;
    let animationFrame;
    
    function animateSpeed() {
        if (currentSpeed < targetSpeed) {
            currentSpeed += Math.random() * 3 + 1;
            if (currentSpeed > targetSpeed) currentSpeed = targetSpeed;
            
            if (speedValue) {
                speedValue.textContent = currentSpeed.toFixed(1);
            }
            
            if (speedRing) {
                const progress = (currentSpeed / 150) * circumference;
                speedRing.style.strokeDashoffset = circumference - Math.min(progress, circumference * 0.9);
            }
            
            animationFrame = requestAnimationFrame(animateSpeed);
        }
    }
    
    // Start animation after a short delay
    setTimeout(animateSpeed, 500);
    
    // Simulate signal changes
    function updateSignal() {
        // Random RSRP between -75 and -100
        const rsrp = -75 - Math.floor(Math.random() * 25);
        const rsrq = -5 - Math.floor(Math.random() * 12);
        const sinr = 5 + Math.floor(Math.random() * 20);
        const band = bands[Math.floor(Math.random() * bands.length)];
        
        // Update values with animation
        if (rsrpVal) {
            rsrpVal.textContent = rsrp;
            rsrpVal.style.color = rsrp > -90 ? '#10b981' : rsrp > -100 ? '#f59e0b' : '#ef4444';
        }
        
        if (rsrqVal) {
            rsrqVal.textContent = rsrq;
        }
        
        if (sinrVal) {
            sinrVal.textContent = sinr;
            sinrVal.style.color = sinr > 15 ? '#10b981' : sinr > 8 ? '#f59e0b' : '#ef4444';
        }
        
        if (bandVal) {
            bandVal.textContent = band;
        }
        
        // Update signal bars
        const activeBars = rsrp > -80 ? 5 : rsrp > -90 ? 4 : rsrp > -100 ? 3 : rsrp > -110 ? 2 : 1;
        bars.forEach((bar, index) => {
            setTimeout(() => {
                bar.classList.toggle('active', index < activeBars);
            }, index * 50);
        });
    }
    
    // Update signal every 4 seconds
    setInterval(updateSignal, 4000);
}

/**
 * Counter Animations
 */
function initCounterAnimations() {
    const counters = document.querySelectorAll('.stat-value');
    
    const observerOptions = {
        root: null,
        threshold: 0.5
    };
    
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);
    
    counters.forEach(counter => observer.observe(counter));
}

/**
 * Smooth scroll to section
 */
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            target.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    });
});

/**
 * Parallax effect on hero
 */
window.addEventListener('scroll', function() {
    const scrolled = window.scrollY;
    const heroVisual = document.querySelector('.hero-visual');
    if (heroVisual && scrolled < window.innerHeight) {
        heroVisual.style.transform = `translateY(${scrolled * 0.3}px)`;
    }
});

/**
 * Button ripple effect
 */
document.querySelectorAll('.btn').forEach(button => {
    button.addEventListener('click', function(e) {
        const rect = this.getBoundingClientRect();
        const ripple = document.createElement('span');
        ripple.style.cssText = `
            position: absolute;
            border-radius: 50%;
            background: rgba(255, 255, 255, 0.3);
            transform: scale(0);
            animation: ripple 0.6s linear;
            pointer-events: none;
        `;
        
        const size = Math.max(rect.width, rect.height);
        ripple.style.width = ripple.style.height = size + 'px';
        ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
        ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';
        
        this.style.position = 'relative';
        this.style.overflow = 'hidden';
        this.appendChild(ripple);
        
        setTimeout(() => ripple.remove(), 600);
    });
});

/**
 * Add ripple animation keyframes
 */
const style = document.createElement('style');
style.textContent = `
    @keyframes ripple {
        to {
            transform: scale(4);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);

/**
 * Lazy load images when they come into view
 */
const lazyImages = document.querySelectorAll('img[src]');
const imageObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            const img = entry.target;
            img.style.opacity = '1';
            imageObserver.unobserve(img);
        }
    });
});

lazyImages.forEach(img => {
    img.style.opacity = '0';
    img.style.transition = 'opacity 0.5s ease';
    imageObserver.observe(img);
});

/**
 * Form validation (if any forms added later)
 */
function validateForm(form) {
    const inputs = form.querySelectorAll('input[required]');
    let isValid = true;
    
    inputs.forEach(input => {
        if (!input.value.trim()) {
            input.classList.add('error');
            isValid = false;
        } else {
            input.classList.remove('error');
        }
    });
    
    return isValid;
}

/**
 * Show notification toast
 */
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <span class="toast-icon">${type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ'}</span>
        <span class="toast-message">${message}</span>
    `;
    toast.style.cssText = `
        position: fixed;
        bottom: 2rem;
        right: 2rem;
        padding: 1rem 1.5rem;
        background: linear-gradient(135deg, rgba(26, 26, 36, 0.95), rgba(20, 20, 28, 0.95));
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 1rem;
        display: flex;
        align-items: center;
        gap: 0.75rem;
        color: white;
        font-size: 0.875rem;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.4);
        backdrop-filter: blur(20px);
        z-index: 9999;
        animation: slideInRight 0.3s ease;
    `;
    
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.style.animation = 'slideOutRight 0.3s ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// Add animation keyframes for toast
const toastStyle = document.createElement('style');
toastStyle.textContent = `
    @keyframes slideInRight {
        from {
            opacity: 0;
            transform: translateX(100%);
        }
        to {
            opacity: 1;
            transform: translateX(0);
        }
    }
    @keyframes slideOutRight {
        from {
            opacity: 1;
            transform: translateX(0);
        }
        to {
            opacity: 0;
            transform: translateX(100%);
        }
    }
`;
document.head.appendChild(toastStyle);
