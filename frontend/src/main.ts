import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Home from './views/Home.vue'
const MapView = () => import('./views/MapView.vue')
const Learn = () => import('./views/Learn.vue')
const Login = () => import('./views/Login.vue')
const Profile = () => import('./views/Profile.vue')
const Review = () => import('./views/Review.vue')
const Projects = () => import('./views/Projects.vue')
import './style.css'
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: Home },
    { path: '/map', component: MapView },
    { path: '/learn/:id', component: Learn },
    { path: '/login', component: Login },
    { path: '/profile', component: Profile },
    { path: '/review', component: Review },
    { path: '/projects', component: Projects },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
createApp(App).use(createPinia()).use(router).mount('#app')
