import { createRouter, createWebHistory } from 'vue-router'

// Use route-level lazy loading so公网首屏只加载当前页面所需资源。
const Home = () => import('./views/Home.vue')
const AsrWorkspace = () => import('./views/AsrWorkspace.vue')
const HomeResult = () => import('./views/HomeResult.vue')
const TextToVoice = () => import('./views/TextToVoice.vue')
const RealtimeVoice = () => import('./views/RealtimeVoice.vue')
const MeetingNotes = () => import('./views/MeetingNotes.vue')
const MeetingNoteDetail = () => import('./views/MeetingNoteDetail.vue')
const NotFound = () => import('./views/NotFound.vue')
const Login = () => import('./views/Login.vue')
const Register = () => import('./views/Register.vue')
const Profile = () => import('./views/Profile.vue')
const VoicePrintCompare = () => import('./views/VoicePrintCompare.vue')
const KnowledgeBase = () => import('./views/KnowledgeBase.vue')
const SystemObservation = () => import('./views/SystemObservation.vue')
const AdminCenter = () => import('./views/AdminCenter.vue')

const routerHistory = createWebHistory()
const router = createRouter({
  history: routerHistory,
  routes: [
    {
      path: '/HomeResult',
      name: 'HomeResult',
      component: HomeResult
    },
    {
      path: '/asr',
      name: 'AsrWorkspace',
      component: AsrWorkspace
    },
    {
      path: '/',
      name: 'Home',
      component: Home
    },
    {
      path: '/login',
      name: 'Login',
      component: Login,
      meta: {
        hideNavMenu: true
      }
    },
    {
      path: '/register',
      name: 'Register',
      component: Register,
      meta: {
        hideNavMenu: true
      }
    },
    {
      path: '/profile',
      name: 'Profile',
      component: Profile
    },
    {
      path: '/TextToVoice',
      name: 'TextToVoice',
      component: TextToVoice
    },
    {
      path: '/RealtimeVoice',
      name: 'RealtimeVoice',
      component: RealtimeVoice
    },
    {
      path: '/MeetingNotes',
      name: 'MeetingNotes',
      component: MeetingNotes
    },
    {
      path: '/MeetingNotes/:meetingId',
      name: 'MeetingNoteDetail',
      component: MeetingNoteDetail
    },
    {
      path: '/VoicePrintCompare',
      name: 'VoicePrintCompare',
      component: VoicePrintCompare
    },
    {
      path: '/knowledge',
      name: 'KnowledgeBase',
      component: KnowledgeBase
    },
    {
      path: '/system-observation',
      name: 'SystemObservation',
      component: SystemObservation
    },
    {
      path: '/admin',
      name: 'AdminCenter',
      component: AdminCenter
    },

    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      component: NotFound
    }
  ]
})
export default router
