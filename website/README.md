# Website

This folder contains the landing page website for Force LTE Only.

## Files

```
website/
├── index.html          # Main landing page
├── privacy.html        # Privacy Policy page
├── terms.html          # Terms of Service page
├── styles.css          # Main stylesheet
├── script.js           # JavaScript functionality
├── images/             # App screenshots and images
│   └── README.md       # Instructions for adding images
└── README.md           # This file
```

## Adding Screenshots

Place your app screenshots in the `images/` folder with these names:

| Filename | Description |
|----------|-------------|
| `screenshot-home.png` | Home screen of the app |
| `screenshot-analytics.png` | Analytics dashboard |
| `screenshot-speedtest.png` | Speed test feature |
| `screenshot-settings.png` | Settings screen |

### Screenshot Guidelines

- **Recommended size**: 1080 x 1920 pixels (9:16 aspect ratio for phone screens)
- **Format**: PNG or WebP
- **Naming**: Use lowercase with hyphens (e.g., `screenshot-home.png`)

## Preview Locally

Open `index.html` directly in your browser, or use a local server:

```bash
# Using Python 3
python -m http.server 8000

# Using Node.js (npx)
npx serve .

# Using PHP
php -S localhost:8000
```

Then visit `http://localhost:8000` in your browser.

## Deployment Options

### GitHub Pages (Recommended for Open Source)

1. Go to your repository Settings > Pages
2. Select "Deploy from a branch"
3. Choose the branch (e.g., `main`) and folder (`/website`)
4. Your site will be available at `https://yourusername.github.io/ForceLTEOnly/`

### Netlify

1. Drag and drop the `website/` folder to [Netlify Drop](https://app.netlify.com/drop)
2. Or connect your GitHub repo and deploy

### Vercel

```bash
npx vercel --prod website/
```

### Cloudflare Pages

1. Connect your GitHub repository to Cloudflare Pages
2. Set the build command and output directory
3. Deploy

## Customization

### Updating App Info

Edit these values in `index.html`:

```html
<!-- App Package ID -->
https://play.google.com/store/apps/details?id=com.pixeleye.lteonly

<!-- GitHub Repository -->
https://github.com/yourusername/ForceLTEOnly
```

### Updating Colors

Modify the CSS variables in `styles.css`:

```css
:root {
    --primary: #6366f1;      /* Main brand color */
    --primary-dark: #4f46e5; /* Darker shade */
    --secondary: #8b5cf6;     /* Accent color */
}
```

### Adding Features

The website uses vanilla HTML, CSS, and JavaScript. No build tools required.
