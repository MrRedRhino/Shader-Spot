<script setup lang="ts">
import {ref} from 'vue'
import {
  darkTheme,
  NCarousel,
  NConfigProvider,
  NDialogProvider,
  NInput,
  NMessageProvider,
  NNotificationProvider
} from 'naive-ui'

const globalImageIndex = ref(0)
const search = ref('')
const searchResults = ref([
  {"id": "1jdWjXFa", name: "AmberVeil Shaders"},
  {"id": "162KpUhg", name: "Vanilla Camera"},
  {"id": "1cVbuPXx", name: "True Light Shader"},
  {"id": "1KNMo6Id", name: "TrueLightFX"},
  {"id": "134lIeeG", name: "Chandler's Retro Shader"},
  {"id": "1BZ68dI8", name: "Pegasus Shaders"},
  {"id": "1UhWoJiz", name: "Ranold's Better Breaking"},
  {"id": "1S4HCfve", name: "Haze Shader"},
  {"id": "1pqbulcm", name: "Ascending fade"},
  {"id": "1sfrgEIk", name: "Haven's Inversion"}
]);

function buildUrl(shaderId: string, resolution: string) {
  return `https://storage.googleapis.com/shader-spot-1/${shaderId}-111111-${resolution}.webp`;
}

function onSearch() {
  // TODO: wire up to the shader search
  console.log('search:', search.value)
}
</script>

<template>
  <NConfigProvider :theme="darkTheme">
    <NMessageProvider>
      <NNotificationProvider>
        <NDialogProvider>
          <div
              class="pointer-events-none fixed inset-x-0 top-0 z-50 flex justify-center px-4 pt-4"
          >
            <div
                class="pointer-events-auto w-full max-w-xl rounded-full border border-neutral-800 bg-neutral-900/80 shadow-lg shadow-black/40 backdrop-blur"
            >
              <NInput
                  v-model:value="search"
                  round
                  clearable
                  size="large"
                  placeholder="Search shaders…"
                  @keyup.enter="onSearch"
              >
                <template #prefix>
                  <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 24 24"
                      width="18"
                      height="18"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                  >
                    <circle cx="11" cy="11" r="7"/>
                    <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  </svg>
                </template>
              </NInput>
            </div>
          </div>

          <div class="flex justify-center">
            <div class="shader-grid max-w-382 w-full mt-20">
              <div v-for="result in searchResults" :key="result.id" class="rounded-xl bg-neutral-800">
                <div>
                  <NCarousel autoplay v-model:current-index="globalImageIndex">
                    <img :src="buildUrl(result.id, 'THUMB')" alt="Slide 1" class="w-full rounded-t-xl"/>
                    <img :src="buildUrl(result.id, 'HQ')" alt="Slide 1" class="w-full rounded-t-xl"/>
                    <img :src="buildUrl(result.id, 'LQ')" alt="Slide 1" class="w-full rounded-t-xl"/>
                  </NCarousel>
                </div>
                <h2 class="text-white pl-4">{{ result.name }}</h2>
              </div>
            </div>
          </div>
        </NDialogProvider>
      </NNotificationProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<style scoped>
.shader-grid {
  display: grid;
  gap: 1em;
  grid-template-columns: repeat(auto-fill, minmax(min(400px, 100%), 1fr));
}
</style>
