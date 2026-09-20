const year = document.querySelector("#year");
if (year) year.textContent = new Date().getFullYear();

const menuToggle = document.querySelector(".menu-toggle");
const siteNav = document.querySelector("#site-nav");

menuToggle?.addEventListener("click", () => {
  const open = menuToggle.getAttribute("aria-expanded") === "true";
  menuToggle.setAttribute("aria-expanded", String(!open));
  siteNav?.classList.toggle("is-open", !open);
});

siteNav?.querySelectorAll("a").forEach((link) => {
  link.addEventListener("click", () => {
    menuToggle?.setAttribute("aria-expanded", "false");
    siteNav.classList.remove("is-open");
  });
});

const slides = [...document.querySelectorAll(".hero-slide")];
const dots = [...document.querySelectorAll(".carousel-dot")];
const counter = document.querySelector("#slide-count");
let activeSlide = 0;
let carouselTimer;

function showSlide(index) {
  activeSlide = (index + slides.length) % slides.length;
  slides.forEach((slide, slideIndex) => slide.classList.toggle("is-active", slideIndex === activeSlide));
  dots.forEach((dot, dotIndex) => {
    const selected = dotIndex === activeSlide;
    dot.classList.toggle("is-active", selected);
    dot.setAttribute("aria-selected", String(selected));
  });
  if (counter) counter.textContent = String(activeSlide + 1).padStart(2, "0") + " / " + String(slides.length).padStart(2, "0");
}

function restartCarousel() {
  window.clearInterval(carouselTimer);
  carouselTimer = window.setInterval(() => showSlide(activeSlide + 1), 4600);
}

document.querySelector('[data-carousel="prev"]')?.addEventListener("click", () => {
  showSlide(activeSlide - 1);
  restartCarousel();
});

document.querySelector('[data-carousel="next"]')?.addEventListener("click", () => {
  showSlide(activeSlide + 1);
  restartCarousel();
});

dots.forEach((dot, index) => dot.addEventListener("click", () => {
  showSlide(index);
  restartCarousel();
}));

if (slides.length > 1) restartCarousel();

const revealObserver = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add("is-visible");
      revealObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });

document.querySelectorAll(".reveal").forEach((element) => revealObserver.observe(element));

const glow = document.querySelector(".cursor-glow");
window.addEventListener("pointermove", (event) => {
  if (window.matchMedia("(pointer: coarse)").matches || !glow) return;
  glow.style.left = event.clientX + "px";
  glow.style.top = event.clientY + "px";
  glow.style.opacity = "1";
});
