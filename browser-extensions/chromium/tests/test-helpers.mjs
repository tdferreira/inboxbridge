export function createFakeButton(text = '') {
  const listeners = new Map()
  return {
    disabled: false,
    textContent: text,
    addEventListener(type, listener) {
      listeners.set(type, listener)
    },
    async click(event = {}) {
      const listener = listeners.get('click')
      if (listener) {
        await listener(event)
      }
    }
  }
}

export function createFakeInput(value = '') {
  const listeners = new Map()
  const input = {
    ariaInvalid: 'false',
    checked: false,
    className: '',
    disabled: false,
    readOnly: false,
    value,
    classList: {
      add(name) {
        if (!this.owner.className.split(/\s+/).includes(name)) {
          this.owner.className = `${this.owner.className} ${name}`.trim()
        }
      },
      remove(name) {
        this.owner.className = this.owner.className
          .split(/\s+/)
          .filter((token) => token && token !== name)
          .join(' ')
      },
      owner: null
    },
    addEventListener(type, listener) {
      listeners.set(type, listener)
    },
    async input(event = {}) {
      const listener = listeners.get('input')
      if (listener) {
        await listener({ target: this, ...event })
      }
    },
    async change(event = {}) {
      const listener = listeners.get('change')
      if (listener) {
        await listener({ target: this, ...event })
      }
    }
  }
  input.classList.owner = input
  return input
}

export function createFakeBanner() {
  return {
    className: 'status-banner',
    hidden: true,
    textContent: ''
  }
}

export function createFakeBlock(text = '') {
  const block = {
    className: '',
    hidden: false,
    textContent: text
  }
  block.classList = {
    add(name) {
      if (!this.owner.className.split(/\s+/).includes(name)) {
        this.owner.className = `${this.owner.className} ${name}`.trim()
      }
    },
    remove(name) {
      this.owner.className = this.owner.className
        .split(/\s+/)
        .filter((token) => token && token !== name)
        .join(' ')
    },
    owner: block
  }
  return block
}

export function createFakeForm() {
  const listeners = new Map()
  const form = {
    addEventListener(type, listener) {
      listeners.set(type, listener)
    },
    async submit() {
      const event = {
        defaultPrevented: false,
        preventDefault() {
          this.defaultPrevented = true
        }
      }
      const listener = listeners.get('submit')
      if (listener) {
        await listener(event)
      }
      return event
    }
  }
  return form
}

export function createFakeList() {
  const items = []
  const list = {
    hidden: true,
    ownerDocument: {
      createElement() {
        return {
          className: '',
          innerHTML: ''
        }
      }
    },
    appendChild(node) {
      items.push(node)
    },
    get items() {
      return items
    }
  }
  Object.defineProperty(list, 'innerHTML', {
    get() {
      return ''
    },
    set(_value) {
      items.length = 0
    }
  })
  return list
}

export function createFakeText() {
  return {
    className: '',
    hidden: false,
    textContent: ''
  }
}

export function createFakeCard(className = '') {
  const card = {
    className,
    classList: {
      add(name) {
        if (!this.owner.className.includes(name)) {
          this.owner.className = `${this.owner.className} ${name}`.trim()
        }
      },
      remove(name) {
        this.owner.className = this.owner.className.replace(new RegExp(`\\b${name}\\b`, 'g'), '').replace(/\s+/g, ' ').trim()
      },
      owner: null
    }
  }
  card.classList.owner = card
  return card
}
