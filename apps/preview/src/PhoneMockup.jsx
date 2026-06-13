export default function PhoneMockup() {
  return (
    <div style={styles.phoneFrame}>
      <div style={styles.statusBar}>
        <span style={styles.time}>9:41</span>
        <div style={styles.statusIcons}>
          <span style={styles.dot} />
          <span style={styles.dot} />
          <span style={styles.dot} />
        </div>
      </div>
      <div style={styles.notch} />
      <div style={styles.screen}>
        {/* Header */}
        <div style={styles.header}>
          <div>
            <span style={styles.appTitle}>ElderCare Guardian</span>
            <span style={styles.patientName}>Ramesh Kumar</span>
          </div>
          <div style={styles.headerRight}>
            <div style={styles.modeSwitch}>
              <span style={{ ...styles.modeTab, ...styles.modeActive }}>Demo</span>
              <span style={styles.modeTab}>BLE</span>
            </div>
            <div style={styles.chip}>Simulator</div>
            <div style={{ ...styles.pill, color: '#0F766E', background: '#0F766E14' }}>
              <span style={styles.pillDot} />
              Safe
            </div>
          </div>
        </div>

        {/* Mode notice */}
        <div style={styles.noticeBar}>Demo stream active for reliable elder-care pitch rehearsals.</div>

        {/* Vitals section */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}><span style={styles.accentBar} />ECG</div>
          <div style={styles.ecgPanel}>
            <div style={styles.ecgGrid}>
              {[...Array(20)].map((_, i) => (
                <div key={i} style={{ ...styles.ecgLine, height: `${8 + Math.sin(i * 0.8) * 6 + (i >= 8 && i <= 12 ? 10 : 0)}px` }} />
              ))}
            </div>
          </div>
        </div>

        {/* ECG Rhythm */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}><span style={styles.accentBar} />ECG rhythm</div>
          <div style={{ ...styles.pill, color: '#059669', background: '#05966914' }}>
            <span style={styles.pillDot} />
            Normal
          </div>
          <span style={styles.rhythmDesc}>Rhythm looks regular, RMSSD in expected range.</span>
        </div>

        {/* Vitals Grid */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}><span style={styles.accentBar} />Vitals</div>
          <div style={styles.metricRow}>
            {[
              { label: 'HR', value: '72', unit: 'bpm' },
              { label: 'SpO2', value: '98.5', unit: '%', dot: '#059669', sub: 'Reliable' },
            ].map(m => (
              <div key={m.label} style={styles.metricCard}>
                <span style={styles.metricLabel}>{m.label}</span>
                <div style={styles.metricValue}>
                  <span style={styles.metricNum}>{m.value}</span>
                  <span style={styles.metricUnit}>{m.unit}</span>
                  {m.dot && <span style={{ ...styles.statusDot, background: m.dot }} />}
                </div>
                {m.sub && <span style={{ ...styles.metricSub, color: m.dot }}>{m.sub}</span>}
              </div>
            ))}
          </div>
          <div style={styles.metricRow}>
            {[
              { label: 'BP', value: '120/80', unit: 'mmHg' },
              { label: 'Temp', value: '36.6', unit: 'C' },
            ].map(m => (
              <div key={m.label} style={styles.metricCard}>
                <span style={styles.metricLabel}>{m.label}</span>
                <div style={styles.metricValue}>
                  <span style={styles.metricNum}>{m.value}</span>
                  <span style={styles.metricUnit}>{m.unit}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Safety */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}><span style={styles.accentBar} />Safety</div>
          <div style={styles.metricRow}>
            <div style={styles.metricCard}>
              <span style={styles.metricLabel}>Fall risk</span>
              <div style={styles.metricValue}>
                <span style={styles.metricNum}>Low</span>
              </div>
            </div>
            <div style={styles.metricCard}>
              <span style={styles.metricLabel}>SOS</span>
              <div style={styles.metricValue}>
                <span style={styles.metricNum}>Off</span>
              </div>
            </div>
          </div>
          <div style={styles.pillRow}>
            <div style={{ ...styles.pill, color: '#059669', background: '#05966914' }}>
              <span style={styles.pillDot} />Good
            </div>
            <div style={{ ...styles.pill, color: '#0F766E', background: '#0F766E14' }}>
              <span style={styles.pillDot} />Normal
            </div>
          </div>
        </div>

        {/* Health Signals */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}><span style={styles.accentBar} />Health</div>
          <SignalRow label="Respiratory rate" value="16 breaths/min" progress={0.62} />
          <SignalRow label="Sweat humidity" value="51.0%" progress={0.51} />
          <SignalRow label="Dehydration risk" value="Low" progress={0.25} barColor="#059669" />
        </div>

        {/* Bottom nav */}
        <div style={styles.bottomNav}>
          {['Vitals', 'Safety', 'Care', 'Ready', 'Settings'].map(t => (
            <div key={t} style={{ ...styles.navItem, ...(t === 'Vitals' ? styles.navActive : {}) }}>
              <div style={styles.navIcon} />
              <span style={t === 'Vitals' ? styles.navLabelActive : styles.navLabel}>{t}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function SignalRow({ label, value, progress, barColor = '#0F766E' }) {
  const pct = Math.min(Math.max(progress * 100, 0), 100);
  return (
    <div style={styles.signalRow}>
      <div style={styles.signalLabels}>
        <span style={styles.signalLabel}>{label}</span>
        <span style={styles.signalValue}>{value}</span>
      </div>
      <div style={styles.progressTrack}>
        <div style={{ ...styles.progressFill, width: `${pct}%`, background: barColor }} />
      </div>
    </div>
  );
}

const styles = {
  phoneFrame: {
    position: 'relative',
    width: '375px',
    height: '780px',
    background: '#1E1E2E',
    borderRadius: '44px',
    padding: '12px',
    boxShadow: '0 25px 60px rgba(0,0,0,0.5), 0 0 0 1px rgba(255,255,255,0.06)',
    flexShrink: 0,
  },
  statusBar: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '8px 20px 0',
    position: 'absolute',
    top: '12px',
    left: '12px',
    right: '12px',
    zIndex: 2,
  },
  time: { fontSize: '14px', fontWeight: 600, color: '#FFFFFF' },
  statusIcons: { display: 'flex', gap: '4px', alignItems: 'center' },
  dot: { width: '6px', height: '6px', borderRadius: '50%', background: '#FFFFFF88' },
  notch: {
    position: 'absolute',
    top: '12px',
    left: '50%',
    transform: 'translateX(-50%)',
    width: '120px',
    height: '28px',
    background: '#1E1E2E',
    borderRadius: '0 0 16px 16px',
    zIndex: 3,
  },
  screen: {
    width: '100%',
    height: '100%',
    background: '#F0F4F8',
    borderRadius: '36px',
    overflowY: 'auto',
    overflowX: 'hidden',
    padding: '0 16px 16px',
    display: 'flex',
    flexDirection: 'column',
    gap: '14px',
    position: 'relative',
  },
  header: {
    paddingTop: '48px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  appTitle: {
    display: 'block',
    fontSize: '22px',
    fontWeight: 800,
    color: '#0F172A',
    letterSpacing: '-0.3px',
  },
  patientName: { display: 'block', fontSize: '13px', color: '#64748B', marginTop: '2px' },
  headerRight: { display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '6px' },
  modeSwitch: {
    display: 'flex',
    borderRadius: '8px',
    border: '1px solid #E2E8F0',
    background: '#FFFFFF',
    padding: '3px',
    gap: '4px',
  },
  modeTab: {
    padding: '4px 10px',
    borderRadius: '6px',
    fontSize: '12px',
    fontWeight: 500,
    color: '#334155',
    cursor: 'pointer',
  },
  modeActive: { background: '#0F766E', color: '#FFFFFF' },
  chip: {
    fontSize: '11px',
    fontWeight: 600,
    color: '#94A3B8',
    background: '#94A3B814',
    borderRadius: '20px',
    padding: '2px 8px',
  },
  pill: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    fontSize: '13px',
    fontWeight: 700,
    borderRadius: '8px',
    padding: '6px 12px',
  },
  pillDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: 'currentColor',
  },
  pillRow: { display: 'flex', gap: '8px', flexWrap: 'wrap' },
  noticeBar: {
    fontSize: '12px',
    fontWeight: 600,
    color: '#1D4ED8',
    background: '#DBEAFE',
    borderRadius: '8px',
    padding: '8px 12px',
    lineHeight: 1.4,
  },
  section: {
    background: '#FFFFFF',
    borderRadius: '10px',
    padding: '14px',
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
    boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
  },
  sectionTitle: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    fontSize: '17px',
    fontWeight: 600,
    color: '#0F172A',
  },
  accentBar: {
    width: '4px',
    height: '18px',
    borderRadius: '2px',
    background: '#0F766E',
    flexShrink: 0,
  },
  ecgPanel: {
    background: '#1A1A2E',
    borderRadius: '8px',
    padding: '16px',
    height: '120px',
    display: 'flex',
    alignItems: 'flex-end',
  },
  ecgGrid: {
    display: 'flex',
    alignItems: 'flex-end',
    gap: '6px',
    width: '100%',
    height: '100%',
  },
  ecgLine: {
    flex: 1,
    background: '#22C55E',
    borderRadius: '2px 2px 0 0',
    minHeight: '3px',
    opacity: 0.9,
  },
  rhythmDesc: { fontSize: '13px', color: '#475569', lineHeight: 1.4, margin: 0 },
  metricRow: { display: 'flex', gap: '10px' },
  metricCard: {
    flex: 1,
    background: '#FFFFFF',
    borderRadius: '10px',
    padding: '12px',
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
    border: '1px solid #F1F5F9',
  },
  metricLabel: { fontSize: '14px', fontWeight: 500, color: '#64748B' },
  metricValue: { display: 'flex', alignItems: 'flex-end', gap: '6px' },
  metricNum: { fontSize: '24px', fontWeight: 800, color: '#0F172A', lineHeight: 1 },
  metricUnit: { fontSize: '13px', color: '#94A3B8', paddingBottom: '2px' },
  metricSub: { fontSize: '11px', fontWeight: 600 },
  statusDot: { width: '8px', height: '8px', borderRadius: '50%', marginBottom: '4px' },
  signalRow: { display: 'flex', flexDirection: 'column', gap: '6px' },
  signalLabels: { display: 'flex', justifyContent: 'space-between' },
  signalLabel: { fontSize: '14px', color: '#475569' },
  signalValue: { fontSize: '14px', fontWeight: 600, color: '#0F172A' },
  progressTrack: {
    width: '100%',
    height: '8px',
    background: '#F1F5F9',
    borderRadius: '4px',
    overflow: 'hidden',
  },
  progressFill: { height: '100%', borderRadius: '4px', transition: 'width 0.3s' },
  bottomNav: {
    display: 'flex',
    justifyContent: 'space-around',
    background: '#FFFFFF',
    borderRadius: '10px',
    padding: '8px 0',
    marginTop: 'auto',
    boxShadow: '0 -1px 2px rgba(0,0,0,0.04)',
  },
  navItem: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '2px',
    cursor: 'pointer',
    padding: '4px 8px',
    borderRadius: '8px',
  },
  navActive: { background: '#0F766E14' },
  navIcon: { width: '20px', height: '20px', borderRadius: '4px', background: '#CBD5E1' },
  navLabel: { fontSize: '11px', color: '#94A3B8', fontWeight: 500 },
  navLabelActive: { fontSize: '11px', color: '#0F766E', fontWeight: 600 },
};
