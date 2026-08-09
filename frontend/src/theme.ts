import { createTheme } from '@mui/material/styles'

export const theme = createTheme({
  colorSchemes: { light: true, dark: true },
  palette: {
    primary: { main: '#5c3d8f' },
    secondary: { main: '#c0392b' },
  },
  shape: { borderRadius: 8 },
  components: {
    MuiLink: {
      defaultProps: {
        color: 'inherit',
      },
    },
  },
})
