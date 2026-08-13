import { defineConfig } from 'vitepress'

const github = 'https://github.com/LostUmbrella58/FakeModBlocker'

function navEn() {
  return [
    { text: 'Guide', link: '/guide/introduction' },
    { text: 'Configuration', link: '/guide/configuration' },
    { text: 'GitHub', link: github }
  ]
}

function navZh() {
  return [
    { text: '指南', link: '/zh/guide/introduction' },
    { text: '配置', link: '/zh/guide/configuration' },
    { text: 'GitHub', link: github }
  ]
}

function sidebarEn() {
  return [
    {
      text: 'Getting Started',
      items: [
        { text: 'Introduction', link: '/guide/introduction' },
        { text: 'Installation', link: '/guide/installation' },
        { text: 'Commands & Permissions', link: '/guide/commands' }
      ]
    },
    {
      text: 'Config & Detection',
      items: [
        { text: 'Configuration', link: '/guide/configuration' },
        { text: 'Channel Detection', link: '/guide/channel-detection' },
        { text: 'Sign Translation Detection', link: '/guide/sign-detection' },
        { text: 'Escalation (Warn → Kick → Ban)', link: '/guide/escalation' },
        { text: 'Messages & Locales', link: '/guide/messages' }
      ]
    },
    {
      text: 'More',
      items: [
        { text: 'Compatibility', link: '/guide/compatibility' },
        { text: 'FAQ', link: '/guide/faq' }
      ]
    }
  ]
}

function sidebarZh() {
  return [
    {
      text: '开始使用',
      items: [
        { text: '简介', link: '/zh/guide/introduction' },
        { text: '安装', link: '/zh/guide/installation' },
        { text: '命令与权限', link: '/zh/guide/commands' }
      ]
    },
    {
      text: '配置与检测',
      items: [
        { text: '配置文件', link: '/zh/guide/configuration' },
        { text: '频道检测', link: '/zh/guide/channel-detection' },
        { text: '告示牌翻译检测', link: '/zh/guide/sign-detection' },
        { text: '阶梯处罚（警告/踢出/封禁）', link: '/zh/guide/escalation' },
        { text: '消息与多语言', link: '/zh/guide/messages' }
      ]
    },
    {
      text: '其他',
      items: [
        { text: '兼容性', link: '/zh/guide/compatibility' },
        { text: '常见问题', link: '/zh/guide/faq' }
      ]
    }
  ]
}

export default defineConfig({
  title: 'FakeModBlocker',
  description:
    'Lightweight, configurable Minecraft mod detection plugin',
  // GitHub Pages project site: https://lostumbrella58.github.io/FakeModBlocker/
  base: '/FakeModBlocker/',
  cleanUrls: true,
  lastUpdated: true,

  locales: {
    root: {
      label: 'English',
      lang: 'en',
      description:
        'Lightweight, configurable Minecraft mod detection plugin',
      themeConfig: {
        nav: navEn(),
        sidebar: sidebarEn(),
        editLink: {
          pattern: `${github}/edit/main/docs/:path`,
          text: 'Edit this page on GitHub'
        },
        outline: {
          label: 'On this page',
          level: [2, 3]
        },
        docFooter: {
          prev: 'Previous',
          next: 'Next'
        },
        lastUpdated: {
          text: 'Last updated'
        },
        darkModeSwitchLabel: 'Theme',
        lightModeSwitchTitle: 'Switch to light mode',
        darkModeSwitchTitle: 'Switch to dark mode',
        sidebarMenuLabel: 'Menu',
        returnToTopLabel: 'Return to top',
        langMenuLabel: 'Language'
      }
    },
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      link: '/zh/',
      description: '轻量、可配置的 Minecraft 模组检测插件',
      themeConfig: {
        nav: navZh(),
        sidebar: sidebarZh(),
        editLink: {
          pattern: `${github}/edit/main/docs/:path`,
          text: '在 GitHub 上编辑此页'
        },
        outline: {
          label: '本页目录',
          level: [2, 3]
        },
        docFooter: {
          prev: '上一页',
          next: '下一页'
        },
        lastUpdated: {
          text: '最后更新'
        },
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到浅色模式',
        darkModeSwitchTitle: '切换到深色模式',
        sidebarMenuLabel: '菜单',
        returnToTopLabel: '回到顶部',
        langMenuLabel: '切换语言'
      }
    }
  },

  themeConfig: {
    socialLinks: [{ icon: 'github', link: github }],
    search: {
      provider: 'local'
    },
    footer: {
      message: 'Released under the MIT License',
      copyright: 'Copyright © Creeper_可能c'
    }
  }
})
