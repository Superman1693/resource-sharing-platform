<template>
  <canvas ref="canvasRef" class="fireworks-canvas"></canvas>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const canvasRef = ref(null)
let ctx, w, h, ratio
let animationId

const gravity = 0.05
const friction = 0.99
const fireworks = []
const particles = []

class Firework {
  constructor() {
    this.x = Math.random() * w
    this.y = h
    this.targetY = Math.random() * h * 0.5 + h * 0.1
    this.speed = Math.random() * 3 + 6
    this.vx = 0
    this.vy = -this.speed
    this.exploded = false
    this.color = `hsl(${Math.random() * 360},100%,50%)`
  }

  update() {
    this.x += this.vx
    this.y += this.vy
    this.vy += gravity * 0.3

    if (this.y <= this.targetY && !this.exploded) {
      this.exploded = true
      explode(this.x, this.y, this.color)
    }
  }

  draw() {
    ctx.beginPath()
    ctx.arc(this.x, this.y, 2, 0, Math.PI * 2)
    ctx.fillStyle = this.color
    ctx.fill()
  }
}

class Particle {
  constructor(x, y, color) {
    const angle = Math.random() * Math.PI * 2
    const speed = Math.random() * 5 + 2
    this.x = x
    this.y = y
    this.vx = Math.cos(angle) * speed
    this.vy = Math.sin(angle) * speed
    this.alpha = 1
    this.color = color
    this.size = Math.random() * 2 + 1
  }

  update() {
    this.vx *= friction
    this.vy *= friction
    this.vy += gravity
    this.x += this.vx
    this.y += this.vy
    this.alpha -= 0.01
  }

  draw() {
    ctx.save()
    ctx.globalAlpha = this.alpha
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
    ctx.fillStyle = this.color
    ctx.fill()
    ctx.restore()
  }
}

function explode(x, y, color) {
  const count = Math.random() * 60 + 80
  for (let i = 0; i < count; i++) {
    particles.push(new Particle(x, y, color))
  }
}

function resize() {
  ratio = window.devicePixelRatio || 1
  w = canvasRef.value.width = window.innerWidth * ratio
  h = canvasRef.value.height = window.innerHeight * ratio
  canvasRef.value.style.width = window.innerWidth + 'px'
  canvasRef.value.style.height = window.innerHeight + 'px'
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
}

function loop() {
  // ✅ 清空画布，但不影响 CSS 背景
  ctx.clearRect(0, 0, w, h)

  if (Math.random() < 0.04) {
    fireworks.push(new Firework())
  }

  fireworks.forEach((f, i) => {
    f.update()
    f.draw()
    if (f.exploded) fireworks.splice(i, 1)
  })

  particles.forEach((p, i) => {
    p.update()
    p.draw()
    if (p.alpha <= 0) particles.splice(i, 1)
  })

  animationId = requestAnimationFrame(loop)
}


onMounted(() => {
  ctx = canvasRef.value.getContext('2d')
  resize()
  window.addEventListener('resize', resize)
  loop()
})

onUnmounted(() => {
  cancelAnimationFrame(animationId)
  window.removeEventListener('resize', resize)
})
</script>

<style scoped>
.fireworks-canvas {
  position: fixed;
  inset: 0;
  z-index: 1;
  pointer-events: none; /* ⭐不挡登录点击 */
}
</style>
