import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
export const BancaAura = definePreset(Aura, {
  semantic: {
    primary: { 50: '#f1f6f0', 100: '#dce9d9', 200: '#bdd6b8', 300: '#90b98b',
      400: '#5f935c', 500: '#367042', 600: '#1C5733', 700: '#194E2E', 800: '#153e26', 900: '#102e1d', 950: '#091b12' },
    colorScheme: { light: {
      primary: { color: '#194E2E', inverseColor: '#ffffff', hoverColor: '#1C5733', activeColor: '#153e26' },
      highlight: { background: '#edf4e9', focusBackground: '#dce9d9', color: '#194E2E', focusColor: '#194E2E' },
      text: { color: '#000000', hoverColor: '#0C2139', mutedColor: '#60645e', hoverMutedColor: '#0C2139' }
    } }
  }
});
