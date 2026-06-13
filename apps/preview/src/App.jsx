import Strands from './Strands';
import PhoneMockup from './PhoneMockup';

export default function App() {
  return (
    <div style={styles.page}>
      <Strands
        colors={['#0F766E', '#059669', '#06B6D4']}
        count={4}
        speed={0.25}
        amplitude={0.7}
        waviness={0.7}
        thickness={0.5}
        glow={1.8}
        taper={3}
        spread={1.2}
        intensity={0.35}
        saturation={1.2}
        opacity={0.5}
        scale={1.3}
      />
      <div style={styles.content}>
        <div style={styles.header}>
          <h1 style={styles.title}>ElderCare Guardian</h1>
          <p style={styles.subtitle}>Redesigned UI Preview</p>
        </div>
        <PhoneMockup />
        <div style={styles.footer}>
          <p style={styles.footerText}>Android app · Jetpack Compose · Material3</p>
          <p style={styles.footerText}>IIT Mandi — Elderly Safety Wearable</p>
        </div>
      </div>
    </div>
  );
}

const styles = {
  page: {
    position: 'relative',
    minHeight: '100vh',
    background: '#0F172A',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily: "'Inter', -apple-system, sans-serif",
    overflow: 'hidden',
  },
  content: {
    position: 'relative',
    zIndex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '32px',
    padding: '40px 20px',
  },
  header: {
    textAlign: 'center',
  },
  title: {
    fontSize: '36px',
    fontWeight: 800,
    color: '#FFFFFF',
    margin: 0,
    letterSpacing: '-0.5px',
  },
  subtitle: {
    fontSize: '16px',
    color: '#94A3B8',
    margin: '8px 0 0 0',
    fontWeight: 400,
  },
  footer: {
    textAlign: 'center',
  },
  footerText: {
    fontSize: '13px',
    color: '#475569',
    margin: '2px 0',
    fontWeight: 400,
  },
};
