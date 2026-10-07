import os
import json
import base64
import requests
from io import BytesIO
from PIL import Image, ImageDraw, ImageFont
import streamlit as st
import streamlit.components.v1 as components

# --- Konfigurace stránky ---
st.set_page_config(
    page_title="NutriCheck AI - Kontrola složení potravin",
    page_icon="🥗",
    layout="centered",
    initial_sidebar_state="collapsed"
)

# --- Skript pro vynucení zadního fotoaparátu a automatického ostření na mobilech ---
components.html("""
<script>
    function setupCameraConstraints() {
        try {
            const doc = window.parent.document;
            // Nastaví capture="environment" na všechny file inputy pro přímé otevření zadního fotoaparátu
            const inputs = doc.querySelectorAll('input[type="file"]');
            inputs.forEach(inp => {
                if (!inp.getAttribute('capture')) {
                    inp.setAttribute('capture', 'environment');
                }
            });
        } catch(e) {}
    }

    try {
        const pNav = window.parent.navigator;
        if (pNav && pNav.mediaDevices && pNav.mediaDevices.getUserMedia) {
            const origGUM = pNav.mediaDevices.getUserMedia.bind(pNav.mediaDevices);
            pNav.mediaDevices.getUserMedia = function(constraints) {
                constraints = constraints || {};
                if (!constraints.video) {
                    constraints.video = {};
                }
                if (typeof constraints.video === 'boolean') {
                    constraints.video = { 
                        facingMode: { ideal: 'environment' },
                        width: { ideal: 1920 },
                        height: { ideal: 1080 }
                    };
                } else {
                    constraints.video.facingMode = { ideal: 'environment' };
                    constraints.video.width = constraints.video.width || { ideal: 1920 };
                    constraints.video.height = constraints.video.height || { ideal: 1080 };
                }
                return origGUM(constraints).then(stream => {
                    const track = stream.getVideoTracks()[0];
                    if (track && 'applyConstraints' in track) {
                        track.applyConstraints({
                            advanced: [{ focusMode: 'continuous' }]
                        }).catch(() => {});
                    }
                    return stream;
                });
            };
        }
    } catch(e) {}

    setInterval(setupCameraConstraints, 800);
</script>
""", height=0, width=0)

# --- Vlastní CSS styly ---
st.markdown("""
<style>
    .main-header {
        text-align: center;
        margin-bottom: 1.5rem;
    }
    .score-card {
        padding: 1.2rem;
        border-radius: 16px;
        text-align: center;
        margin-bottom: 1rem;
    }
    .score-healthy {
        background-color: #E8F5E9;
        border: 2px solid #2E7D32;
        color: #1B5E20;
    }
    .score-moderate {
        background-color: #FFF3E0;
        border: 2px solid #F57C00;
        color: #E65100;
    }
    .score-unhealthy {
        background-color: #FFEBEE;
        border: 2px solid #D32F2F;
        color: #B71C1C;
    }
    
    /* Celá škála Nutri-Score A-E */
    .nutriscore-wrapper {
        text-align: center;
        background: #F8F9FA;
        padding: 14px 10px;
        border-radius: 16px;
        border: 1px solid #E0E0E0;
    }
    .nutriscore-scale {
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 6px;
        margin-top: 8px;
    }
    .nutri-box {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        border-radius: 8px;
        font-weight: 800;
        color: white;
        transition: all 0.2s ease-in-out;
    }
    .nutri-box.inactive {
        width: 32px;
        height: 38px;
        font-size: 1.1rem;
        opacity: 0.35;
    }
    .nutri-box.active {
        width: 48px;
        height: 56px;
        font-size: 1.8rem;
        box-shadow: 0 4px 12px rgba(0,0,0,0.25);
        border: 3px solid #222;
        transform: scale(1.1);
        z-index: 2;
    }
    .nutri-box.A { background-color: #038141; }
    .nutri-box.B { background-color: #85BB2F; }
    .nutri-box.C { background-color: #FECB02; color: #222; }
    .nutri-box.D { background-color: #EE8100; }
    .nutri-box.E { background-color: #E63E11; }
    
    .child-card {
        padding: 14px 18px;
        border-radius: 14px;
        margin-bottom: 16px;
    }
    .child-suitable {
        background-color: #E8F5E9;
        border-left: 6px solid #2E7D32;
    }
    .child-unsuitable {
        background-color: #FFEBEE;
        border-left: 6px solid #D32F2F;
    }
    .child-caution {
        background-color: #FFF3E0;
        border-left: 6px solid #F57C00;
    }
</style>
""", unsafe_allow_html=True)

# --- Získání Gemini API Klíče ---
def get_api_key():
    if "GEMINI_API_KEY" in st.secrets:
        return st.secrets["GEMINI_API_KEY"]
    env_key = os.getenv("GEMINI_API_KEY")
    if env_key:
        return env_key
    return st.session_state.get("custom_api_key", "")

# --- Volání Gemini REST API s automatickým přepínáním modelů ---
MODELS_TO_TRY = [
    "gemini-flash-latest",
    "gemini-3.1-flash-lite-preview",
    "gemini-3.5-flash",
    "gemini-3.8-flash"
]

def analyze_food_with_gemini(image_bytes: bytes, api_key: str, preferred_model: str = "Automaticky") -> tuple[dict, str]:
    base64_img = base64.b64encode(image_bytes).decode("utf-8")
    
    prompt = """
    Jsi expertní nutriční specialista a certifikovaný biochemik specializující se na analýzu složení potravin a aditiv.
    Analyzuj přiloženou fotografii složení potraviny (ingredience a případně nutriční tabulku).
    
    Detailně zkontroluj:
    1. Všechny ingredience (pořadí určuje množství v potravině).
    2. Detekuj rizikové látky: přidaný cukr (glukózo-fruktózový sirup, maltodextrin), palmový tuk, ztužené tuky, transmastné kyseliny, přemíru soli.
    3. Identifikuj všechna éčka s kódy E-XXX. U KAŽDÉHO NEZDRAVÉHO/RIZIKOVÉHO ÉČKA detailně popiš v poli 'healthEffects', CO V TĚLE ZPŮSOBUJE (např. hyperaktivita u dětí, alergie, kožní vyrážky, zažívací potíže, karcinogenní potenciál při vysokých dávkách apod.).
    4. Zhodnoť, zda je potravina vhodná pro děti (suitableForChildren: true/false). Uveď detailní důvod v 'childrenSuitabilityReason', proč ano či proč ne (např. moc cukru, umělá barviva způsobující nepozornost, kofein, riziková éčka).
    5. Zhodnoť celkovou zdravost na škále 0-100 a urči Nutri-Score (A, B, C, D nebo E).
    6. Pokud obrázek NEOBSAHUJE potravinu ani její složení, nastav verdict na "NOT_FOOD".
    
    Vrať POUZE validní JSON v tomto přesném formátu bez jakéhokoliv dalšího textu okolo:
    {
      "productName": "Název výrobku (nebo odhad)",
      "healthScore": 75,
      "verdict": "HEALTHY",
      "verdictTitle": "Zdravá volba",
      "summary": "Stručné české shrnutí v 2-3 větách, zda je potravina zdravá a proč.",
      "nutriScore": "B",
      "suitableForChildren": true,
      "childrenSuitabilityVerdict": "Vhodné pro děti / Nevhodné pro děti / Omezeně pro děti",
      "childrenSuitabilityReason": "Konkrétní vysvětlení, proč je/není vhodné pro děti.",
      "positiveIngredients": ["Ovesné vločky (vláknina)", "Ořechy"],
      "concerningIngredients": [
        {
          "name": "Glukózový sirup",
          "reason": "Rychlý cukr způsobující výkyvy glykémie",
          "riskLevel": "HIGH"
        }
      ],
      "additives": [
        {
          "code": "E250",
          "name": "Dusitan sodný",
          "purpose": "Konzervant",
          "safetyNote": "Syntetický konzervant masa",
          "risk": "HARMFUL",
          "healthEffects": "Při zahřátí může tvořit karcinogenní nitrosaminy; může vyvolat bolesti hlavy nebo alergické reakce."
        }
      ],
      "recommendation": "Vhodné pro běžnou konzumaci jako součást pestré stravy.",
      "healthierAlternative": "Vyzkoušejte ovesné vločky bez přidaného cukru s čerstvým ovocem.",
      "rawIngredientsText": "Přepsané detekované složení..."
    }
    """

    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt},
                    {
                        "inlineData": {
                            "mimeType": "image/jpeg",
                            "data": base64_img
                        }
                    }
                ]
            }
        ],
        "generationConfig": {
            "responseMimeType": "application/json",
            "temperature": 0.2
        }
    }

    headers = {"Content-Type": "application/json; charset=utf-8"}
    
    candidate_models = [preferred_model] if preferred_model != "Automaticky" else MODELS_TO_TRY
    if preferred_model != "Automaticky" and preferred_model not in candidate_models:
        candidate_models = [preferred_model] + [m for m in MODELS_TO_TRY if m != preferred_model]
    else:
        candidate_models = MODELS_TO_TRY

    last_error = None
    for model_name in candidate_models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=60)
            
            if response.status_code in [503, 429]:
                last_error = f"Model {model_name} je dočasně přetížen ({response.status_code})."
                continue
                
            if response.status_code != 200:
                last_error = f"Chyba Gemini API ({response.status_code}) u modelu {model_name}: {response.text}"
                continue
                
            data = response.json()
            candidates = data.get("candidates", [])
            if not candidates:
                last_error = f"Model {model_name} nevrátil žádnou odpověď."
                continue
                
            text_content = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
            
            cleaned = text_content.strip()
            if cleaned.startswith("```json"):
                cleaned = cleaned[7:]
            elif cleaned.startswith("```"):
                cleaned = cleaned[3:]
            if cleaned.endswith("```"):
                cleaned = cleaned[:-3]
                
            parsed_data = json.loads(cleaned.strip())
            return parsed_data, model_name
        except Exception as e:
            last_error = str(e)
            continue
            
    raise Exception(f"Nepodařilo se spojit s žádným modelem Gemini. Poslední hlášení: {last_error}")

# --- Doplňující dotazy k produktu ---
def ask_followup_question(analysis: dict, question: str, api_key: str, model: str = "gemini-flash-latest") -> str:
    models = [model, "gemini-3.1-flash-lite-preview", "gemini-flash-latest"]
    prompt = f"""
    Jsi nutriční poradce. Uživatel analyzoval potravinu:
    - Produkt: {analysis.get('productName')}
    - Skóre: {analysis.get('healthScore')}/100, Nutri-Score: {analysis.get('nutriScore')}
    - Složení: {analysis.get('rawIngredientsText')}
    - Pozitiva: {', '.join(analysis.get('positiveIngredients', []))}
    - Rizika: {', '.join([c.get('name') for c in analysis.get('concerningIngredients', [])])}
    - Pro děti: {analysis.get('childrenSuitabilityVerdict')} ({analysis.get('childrenSuitabilityReason')})
    
    Otázka uživatele: "{question}"
    Odpověz věcně a srozumitelně v češtině (1-2 odstavce).
    """
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.7}
    }
    
    for m in models:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{m}:generateContent?key={api_key}"
        try:
            res = requests.post(url, json=payload, headers={"Content-Type": "application/json; charset=utf-8"}, timeout=30)
            if res.status_code == 200:
                return res.json()["candidates"][0]["content"]["parts"][0]["text"]
        except Exception:
            continue
    return "Omlouvám se, na dotaz se nepodařilo odpovědět."

# --- Pomocná funkce pro bezchybnou tvorbu českých ukázkových štítků s diakritikou ---
def get_unicode_font(size=22, bold=False):
    font_candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "DejaVuSans.ttf",
        "arial.ttf"
    ]
    for fp in font_candidates:
        if os.path.exists(fp):
            try:
                return ImageFont.truetype(fp, size)
            except Exception:
                pass
    return ImageFont.load_default()

def generate_sample_image(title: str, text: str, nutrients: str = "") -> bytes:
    # Vytvoření štítku s vysokým rozlišením pro precizní OCR
    width, height = 900, 520
    img = Image.new('RGB', (width, height), color=(252, 250, 246))
    draw = ImageDraw.Draw(img)
    
    # Rámeček obalu
    draw.rectangle([25, 25, width - 25, height - 25], outline=(200, 200, 200), width=3)
    
    font_header = get_unicode_font(28, bold=True)
    font_sub = get_unicode_font(22, bold=True)
    font_text = get_unicode_font(20, bold=False)
    
    draw.text((45, 45), f"ETIKETA: {title}", fill=(20, 75, 45), font=font_header)
    draw.line([(45, 85), (width - 45, 85)], fill=(210, 210, 210), width=2)
    
    draw.text((45, 100), "SLOŽENÍ VÝROBKU:", fill=(30, 30, 30), font=font_sub)
    
    # Zalamování českého textu
    words = text.split(" ")
    lines = []
    curr = ""
    for w in words:
        test_line = f"{curr} {w}".strip()
        # Měření šířky
        bbox = draw.textbbox((0, 0), test_line, font=font_text)
        if (bbox[2] - bbox[0]) < 800:
            curr = test_line
        else:
            lines.append(curr)
            curr = w
    if curr:
        lines.append(curr)
        
    y = 135
    for l in lines:
        draw.text((45, y), l, fill=(45, 45, 45), font=font_text)
        y += 28
        
    if nutrients:
        draw.line([(45, y + 10), (width - 45, y + 10)], fill=(210, 210, 210), width=2)
        y += 22
        draw.text((45, y), "VÝŽIVOVÉ ÚDAJE NA 100g:", fill=(30, 30, 30), font=font_sub)
        y += 30
        draw.text((45, y), nutrients, fill=(60, 60, 60), font=font_text)
        
    buf = BytesIO()
    img.save(buf, format="JPEG", quality=95)
    return buf.getvalue()

# --- Postranní panel ---
with st.sidebar:
    st.header("⚙️ Nastavení")
    configured_key = get_api_key()
    if not configured_key:
        api_input = st.text_input("Vložte Gemini API klíč:", type="password")
        if api_input:
            st.session_state["custom_api_key"] = api_input
            configured_key = api_input
    else:
        st.success("✅ Gemini API klíč je aktivní")

    model_choice = st.selectbox(
        "🧠 Výběr modelu:",
        ["Automaticky", "gemini-flash-latest", "gemini-3.1-flash-lite-preview", "gemini-3.5-flash", "gemini-3.8-flash"],
        index=0,
        help="Režim 'Automaticky' zkusí nejstabilnější model a při přetížení (503) se sám přepne na záložní."
    )
        
    st.markdown("---")
    st.markdown("""
    **NutriCheck Web**
    Vyfoťte složení potraviny a nechte AI vyhodnotit:
    - 🥗 Zda je potravina zdravá
    - 👶 Zda je vhodná pro děti a proč
    - 📊 Celou škálu Nutri-Score (A–E)
    - ⚠️ Skrytý cukr a rizikové tuky
    - 🧪 Seznam éček a co konkrétně způsobují
    """)

# --- Hlavní zobrazení ---
st.markdown("<div class='main-header'><h1>🥗 NutriCheck AI</h1><p>Vyfoťte složení potraviny a zjistěte, zda je zdravá</p></div>", unsafe_allow_html=True)

# Vstupy: Zadní fotoaparát s ostřením vs Přímý náhled v prohlížeči vs Ukázky
tab_native, tab_browser, tab_sample = st.tabs([
    "📸 Zadní fotoaparát (Doporučeno pro ostrý text)", 
    "📹 Webkamera v prohlížeči", 
    "💡 Vyzkoušet ukázku"
])

image_to_analyze = None

with tab_native:
    st.markdown("##### 📸 Spustit zadní fotoaparát vašeho telefonu")
    st.success("""
    🎯 **Doporučený postup pro ostrý a nezkreslený text:**
    1. Klepněte na tlačítko níže ➔ na mobilu se přímo otevře **zadní systémový fotoaparát**.
    2. **Klepněte prstem na displej telefonu na text etikety**, aby čočka opticky **zaostřila**.
    3. Vyfoťte složení ze vzdálenosti cca 15–20 cm a potvrďte.
    """)
    uploaded_pic = st.file_uploader(
        "Klepněte sem pro spuštění zadního fotoaparátu / výběr fotky:", 
        type=["jpg", "jpeg", "png", "webp"],
        key="rear_camera_uploader"
    )
    if uploaded_pic:
        image_to_analyze = uploaded_pic.getvalue()

with tab_browser:
    st.markdown("##### 📹 Přímý náhled kamery v prohlížeči")
    st.info("💡 Skript automaticky požaduje zadní kameru a průběžné ostření. Pokud přesto vidíte přední kameru, klepněte na ikonu otočení fotoaparátu **🔄** v rohu okna náhledu.")
    camera_pic = st.camera_input("Zamiřte kameru na složení:", key="browser_camera_input")
    if camera_pic:
        image_to_analyze = camera_pic.getvalue()

with tab_sample:
    st.write("Nemáte u sebe potravinu? Vyberte si ukázku s českou diakritikou:")
    col1, col2, col3 = st.columns(3)
    with col1:
        if st.button("🥣 Ovesné vločky"):
            image_to_analyze = generate_sample_image(
                title="Bio Ovesné vločky s oříšky",
                text="Celozrnné ovesné vločky 75 %, pražené lískové ořechy 10 %, lněná semínka 8 %, dýňová a chia semínka 7 %. Přírodní produkt bez přidaného cukru a bez konzervantů.",
                nutrients="Energie: 1650 kJ / 393 kcal | Vláknina: 11,6 g | Bílkoviny: 14,2 g | Cukry: 1,8 g"
            )
    with col2:
        if st.button("🍫 Čokoládová tyčinka"):
            image_to_analyze = generate_sample_image(
                title="Karamelová čokoládová tyčinka",
                text="Cukr, glukózovo-fruktózový sirup, palmový tuk, kakaové máslo, sušené odstředěné mléko, emulgátor E322 (sójový lecitin), polyglycerolpolyricinoleát E476, umělá aromata, jedlá sůl.",
                nutrients="Energie: 2180 kJ / 522 kcal | Cukry: 51,4 g | Nasycené mastné kyseliny: 16,8 g"
            )
    with col3:
        if st.button("🥫 Masová paštika"):
            image_to_analyze = generate_sample_image(
                title="Jemná játrová paštika",
                text="Vepřové sádlo, vepřové maso 25 %, voda, vepřová játra 18 %, škrob E1422, solicí směs (jedlá sůl, konzervant: dusitan sodný E250), stabilizátory E450 a E451, glutamát sodný E621.",
                nutrients="Tuky: 31,0 g | Nasycené tuky: 11,5 g | Sůl: 1,9 g"
            )

# Analýza obrázku
if image_to_analyze:
    api_key = get_api_key()
    if not api_key:
        st.warning("⚠️ Pro spuštění analýzy prosím vložte Gemini API klíč v levém panelu nebo jej nastavte v nastavení Streamlit Secrets.")
    else:
        st.image(image_to_analyze, caption="Analyzovaný snímek", use_container_width=True)
        
        with st.spinner("🤖 Gemini AI čte české složení z etikety, ověřuje éčka a vhodnost pro děti..."):
            try:
                res, used_model = analyze_food_with_gemini(image_to_analyze, api_key, model_choice)
                res["_used_model"] = used_model
                st.session_state["last_analysis"] = res
            except Exception as e:
                st.error(f"Chyba při analýze: {str(e)}")

# Zobrazení výsledků analýzy
if "last_analysis" in st.session_state:
    res = st.session_state["last_analysis"]
    
    st.markdown("---")
    st.subheader(f"📋 Výsledek: {res.get('productName', 'Potravina')}")
    if "_used_model" in res:
        st.caption(f"⚡ Analyzováno modelem: `{res['_used_model']}`")
    
    score = res.get("healthScore", 50)
    verdict = res.get("verdict", "MODERATE")
    active_nutri = res.get("nutriScore", "C").strip().upper()
    
    # Barevná karta se skóre
    score_class = "score-healthy" if score >= 70 else ("score-moderate" if score >= 45 else "score-unhealthy")
    
    col_score, col_nutri = st.columns([1, 1])
    with col_score:
        st.markdown(f"""
        <div class='score-card {score_class}'>
            <p style='margin:0 0 4px 0; font-size: 0.9rem; text-transform: uppercase;'>Skóre zdravosti</p>
            <h2 style='margin:0; font-size: 2.4rem;'>{score} / 100</h2>
            <p style='margin:4px 0 0 0; font-weight: bold;'>{res.get('verdictTitle', '')}</p>
        </div>
        """, unsafe_allow_html=True)
        
    with col_nutri:
        # CELÁ ŠKÁLA NUTRI-SCORE A-E SE ZVÝRAZNĚNÝM SKÓRE
        grades = ["A", "B", "C", "D", "E"]
        scale_html = "<div class='nutriscore-scale'>"
        for g in grades:
            is_active = (g == active_nutri)
            cls = "active" if is_active else "inactive"
            scale_html += f"<div class='nutri-box {g} {cls}'>{g}</div>"
        scale_html += "</div>"
        
        st.markdown(f"""
        <div class='nutriscore-wrapper'>
            <p style='margin:0; font-size: 0.85rem; font-weight: bold; color: #555;'>OFICIÁLNÍ ŠKÁLA NUTRI-SCORE</p>
            {scale_html}
            <p style='margin:6px 0 0 0; font-size: 0.85rem; color: #333;'>Výsledná známka: <b>{active_nutri}</b></p>
        </div>
        """, unsafe_allow_html=True)

    # 1. VHODNOST PRO DĚTI (Nové a požadované)
    suitable_children = res.get("suitableForChildren", True)
    child_verdict = res.get("childrenSuitabilityVerdict", "Vhodné pro děti" if suitable_children else "Nevhodné pro děti")
    child_reason = res.get("childrenSuitabilityReason", "")
    
    child_class = "child-suitable" if suitable_children else "child-unsuitable"
    child_icon = "👶✅" if suitable_children else "👶⚠️"
    
    st.markdown(f"""
    <div class='child-card {child_class}'>
        <h4 style='margin:0 0 6px 0;'>{child_icon} Vhodnost pro děti: <b>{child_verdict}</b></h4>
        <p style='margin:0; font-size: 0.95rem;'>{child_reason}</p>
    </div>
    """, unsafe_allow_html=True)

    # 2. Celkové shrnutí
    st.info(f"**Shrnutí složení:**\n\n{res.get('summary', '')}")
    
    # 3. Pozitiva vs Rizika
    col_pos, col_neg = st.columns(2)
    with col_pos:
        st.success("✅ **Zdravé a přínosné složky:**")
        positives = res.get("positiveIngredients", [])
        if positives:
            for p in positives:
                st.write(f"• {p}")
        else:
            st.write("Žádné výrazné nutriční benefity.")
            
    with col_neg:
        st.error("⚠️ **Problematické složky:**")
        concerns = res.get("concerningIngredients", [])
        if concerns:
            for c in concerns:
                st.write(f"• **{c.get('name')}**: {c.get('reason')}")
        else:
            st.write("Nebyly zjištěny rizikové složky.")

    # 4. ÉČKA A CO ZPŮSOBUJÍ (Nové a požadované)
    additives = res.get("additives", [])
    if additives:
        with st.expander(f"🧪 Detekovaná éčka a jejich zdravotní dopady ({len(additives)})", expanded=True):
            for a in additives:
                risk_emoji = "🟢" if a.get("risk") == "SAFE" else ("🟠" if a.get("risk") == "CAUTION" else "🔴")
                risk_label = "Bezpečné" if a.get("risk") == "SAFE" else ("S výhradami" if a.get("risk") == "CAUTION" else "Rizikové / Škodlivé")
                
                st.markdown(f"**{risk_emoji} {a.get('code')} – {a.get('name')}** *({a.get('purpose', 'Aditivum')})* – `{risk_label}`")
                
                # Zdravotní dopad
                effects = a.get("healthEffects")
                if effects:
                    st.markdown(f"> **⚠️ Co v těle způsobuje:** {effects}")
                elif a.get("safetyNote"):
                    st.caption(f"Poznámka: {a.get('safetyNote')}")
                st.markdown("---")

    # 5. Doporučení a alternativa
    st.markdown(f"💡 **Doporučení ke konzumaci:** {res.get('recommendation', '')}")
    if res.get("healthierAlternative"):
        st.markdown(f"🌱 **Zdravější alternativa:** {res.get('healthierAlternative', '')}")

    # 6. Interaktivní dotazy na Gemini
    st.markdown("---")
    st.subheader("💬 Zeptejte se Gemini na toto složení")
    q_col1, q_col2, q_col3 = st.columns(3)
    quick_q = None
    with q_col1:
        if st.button("Alergeny v produktu?"):
            quick_q = "Obsahuje tato potravina lepek, laktózu, sóju či ořechy?"
    with q_col2:
        if st.button("Je vhodné při hubnutí?"):
            quick_q = "Hodí se tato potravina při redukční dietě na hubnutí?"
    with q_col3:
        if st.button("Vliv na trávení?"):
            quick_q = "Jak tato potravina ovlivňuje zažívání a střevní mikrobiom?"

    user_q = st.text_input("Nebo napište vlastní dotaz:", value=quick_q if quick_q else "")
    if st.button("Odeslat dotaz"):
        if user_q:
            api_key = get_api_key()
            with st.spinner("Gemini odpovídá..."):
                answer = ask_followup_question(res, user_q, api_key, res.get("_used_model", "gemini-flash-latest"))
                st.write(f"**Odpověď:** {answer}")
