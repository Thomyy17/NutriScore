import os
import json
import base64
import requests
from io import BytesIO
from PIL import Image, ImageDraw, ImageFont
import streamlit as st

# --- Konfigurace stránky ---
st.set_page_config(
    page_title="NutriCheck AI - Kontrola složení potravin",
    page_icon="🥗",
    layout="centered",
    initial_sidebar_state="collapsed"
)

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
    .nutri-badge {
        display: inline-block;
        padding: 6px 14px;
        border-radius: 8px;
        font-weight: 800;
        font-size: 1.2rem;
        color: white;
    }
    .nutri-A { background-color: #038141; }
    .nutri-B { background-color: #85BB2F; }
    .nutri-C { background-color: #FECB02; color: black; }
    .nutri-D { background-color: #EE8100; }
    .nutri-E { background-color: #E63E11; }
    .ingredient-card {
        padding: 10px 14px;
        border-radius: 10px;
        margin-bottom: 8px;
    }
</style>
""", unsafe_allow_html=True)

# --- Získání Gemini API Klíče ---
def get_api_key():
    # 1. Ze Streamlit secrets (Streamlit Cloud)
    if "GEMINI_API_KEY" in st.secrets:
        return st.secrets["GEMINI_API_KEY"]
    # 2. Z proměnné prostředí
    env_key = os.getenv("GEMINI_API_KEY")
    if env_key:
        return env_key
    # 3. Zadaný uživatelem v postranním panelu
    return st.session_state.get("custom_api_key", "")

# --- Volání Gemini REST API s automatickým přepínáním modelů (Fallback proti chybě 503) ---
MODELS_TO_TRY = [
    "gemini-flash-latest",
    "gemini-3.1-flash-lite-preview",
    "gemini-3.5-flash",
    "gemini-3.8-flash"
]

def analyze_food_with_gemini(image_bytes: bytes, api_key: str, preferred_model: str = "Automaticky") -> tuple[dict, str]:
    base64_img = base64.b64encode(image_bytes).decode("utf-8")
    
    prompt = """
    Jsi expertní nutriční specialista a biochemik specializující se na analýzu složení potravin a aditiv.
    Analyzuj přiloženou fotografii složení potraviny (ingredience a nutriční tabulku).
    
    Zkontroluj:
    1. Všechny ingredience (pořadí určuje množství v potravině).
    2. Detekuj rizikové látky: přidaný cukr (glukózo-fruktózový sirup), palmový tuk, transmastné kyseliny, přemíru soli.
    3. Identifikuj éčka s kódy E-XXX, popiš jejich bezpečnost a účel.
    4. Zhodnoť celkovou zdravost na škále 0-100 a urči Nutri-Score (A-E).
    5. Pokud obrázek NEOBSAHUJE potravinu ani její složení, nastav verdict na "NOT_FOOD".
    
    Vrať POUZE validní JSON v tomto přesném formátu bez jakéhokoliv dalšího textu okolo:
    {
      "productName": "Název výrobku (nebo odhad)",
      "healthScore": 75,
      "verdict": "HEALTHY",
      "verdictTitle": "Zdravá volba",
      "summary": "Stručné české shrnutí v 2-3 větách, zda je potravina zdravá a proč.",
      "nutriScore": "B",
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
          "code": "E322",
          "name": "Sójový lecitin",
          "purpose": "Emulgátor",
          "safetyNote": "Přírodní, bezpečný",
          "risk": "SAFE"
        }
      ],
      "recommendation": "Vhodné pro běžnou konzumaci jako součást pestré stravy.",
      "healthierAlternative": "Vyzkoušejte ovesné vločky bez přidaného cukru s ovocem.",
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

    headers = {"Content-Type": "application/json"}
    
    # Seznam modelů k pokusu
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
            
            # Pokud model hlásí 503 (vytíženo) nebo 429 (limit), zkusíme okamžitě další model
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
            
            # Vyčištění případných markdown značek
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
            res = requests.post(url, json=payload, headers={"Content-Type": "application/json"}, timeout=30)
            if res.status_code == 200:
                return res.json()["candidates"][0]["content"]["parts"][0]["text"]
        except Exception:
            continue
    return "Omlouvám se, na dotaz se nepodařilo odpovědět."

# --- Pomocná funkce pro ukázkové štítky ---
def generate_sample_image(text: str) -> bytes:
    img = Image.new('RGB', (700, 450), color=(250, 248, 245))
    draw = ImageDraw.Draw(img)
    draw.rectangle([20, 20, 680, 430], outline=(180, 180, 180), width=3)
    draw.text((40, 40), "ETIKETA VÝROBKU - SLOŽENÍ", fill=(30, 80, 50))
    
    words = text.split(" ")
    lines = []
    curr = ""
    for w in words:
        if len(curr + " " + w) < 45:
            curr = curr + " " + w if curr else w
        else:
            lines.append(curr)
            curr = w
    if curr:
        lines.append(curr)
        
    y = 90
    for l in lines:
        draw.text((40, y), l, fill=(40, 40, 40))
        y += 26
        
    buf = BytesIO()
    img.save(buf, format="JPEG")
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
    Vyfoťte složení potraviny svým telefonem a nechte AI vyhodnotit:
    - 🥗 Zda je potravina zdravá
    - 📊 Nutri-Score (A–E)
    - ⚠️ Skrytý cukr a rizikové tuky
    - 🧪 Seznam a bezpečnost éček
    """)

# --- Hlavní zobrazení ---
st.markdown("<div class='main-header'><h1>🥗 NutriCheck AI</h1><p>Vyfoťte složení potraviny a zjistěte, zda je zdravá</p></div>", unsafe_allow_html=True)

# Vstupy: Fotoaparát telefonu vs Nahrání souboru vs Ukázka
tab_camera, tab_upload, tab_sample = st.tabs(["📸 Vyfotit fotoaparátem", "📁 Nahrát obrázek", "💡 Vyzkoušet ukázku"])

image_to_analyze = None

with tab_camera:
    st.info("💡 Na mobilu po kliknutí níže můžete přímo použít zadní fotoaparát vašeho telefonu.")
    camera_pic = st.camera_input("Vyfoťte etiketu se složením:")
    if camera_pic:
        image_to_analyze = camera_pic.getvalue()

with tab_upload:
    uploaded_pic = st.file_uploader("Nahrajte fotku složení z galerie:", type=["jpg", "jpeg", "png", "webp"])
    if uploaded_pic:
        image_to_analyze = uploaded_pic.getvalue()

with tab_sample:
    st.write("Nemáte u sebe potravinu? Vyberte si ukázku:")
    col1, col2, col3 = st.columns(3)
    with col1:
        if st.button("🥣 Ovesné vločky (Zdravé)"):
            image_to_analyze = generate_sample_image(
                "Celozrnné ovesné vločky 75%, pražené lískové ořechy 10%, lněná semínka 8%, chia semínka 7%. Výživové hodnoty na 100g: Vláknina 11.5g, Bílkoviny 14g, Cukry 1.6g, Nasycené tuky 1.5g."
            )
    with col2:
        if st.button("🍫 Čokoládová tyčinka (Nezdravé)"):
            image_to_analyze = generate_sample_image(
                "Cukr, glukózovo-fruktózový sirup, palmový tuk, kakaové máslo, sušené plnotučné mléko, emulgátor E322 (sójový lecitin), E476, umělá aromata, sůl. Cukry 55g na 100g, nasycený tuk 18g."
            )
    with col3:
        if st.button("🥫 Paštika s éčky"):
            image_to_analyze = generate_sample_image(
                "Vepřové sádlo, vepřové maso 20%, voda, játra 15%, škrob E1422, solicí směs (jedlá sůl, konzervant: dusitan sodný E250), stabilizátory E450, E451, glutamát sodný E621, sůl 2.1g."
            )

# Analýza obrázku
if image_to_analyze:
    api_key = get_api_key()
    if not api_key:
        st.warning("⚠️ Pro spuštění analýzy prosím vložte Gemini API klíč v levém panelu nebo jej nastavte v nastavení Streamlit Secrets.")
    else:
        st.image(image_to_analyze, caption="Vyfocená etiketa", use_container_width=True)
        
        with st.spinner("🤖 Gemini AI čte text z etikety, analyzuje nutrienty a ověřuje éčka..."):
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
    nutri = res.get("nutriScore", "C").upper()
    
    # Barevná karta se skóre
    score_class = "score-healthy" if score >= 70 else ("score-moderate" if score >= 45 else "score-unhealthy")
    
    col_score, col_nutri = st.columns([2, 1])
    with col_score:
        st.markdown(f"""
        <div class='score-card {score_class}'>
            <h2 style='margin:0; font-size: 2.2rem;'>{score} / 100</h2>
            <p style='margin:0; font-weight: bold;'>{res.get('verdictTitle', '')}</p>
        </div>
        """, unsafe_allow_html=True)
        
    with col_nutri:
        st.markdown(f"""
        <div style='text-align: center; padding: 1.2rem; background: #F5F5F5; border-radius: 16px;'>
            <p style='margin:0 0 6px 0; font-size: 0.85rem; color: #666;'>NUTRI-SCORE</p>
            <span class='nutri-badge nutri-{nutri}'>{nutri}</span>
        </div>
        """, unsafe_allow_html=True)

    # Shrnutí
    st.info(f"**Shrnutí složení:**\n\n{res.get('summary', '')}")
    
    # Pozitiva vs Rizika
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

    # Éčka a aditiva
    additives = res.get("additives", [])
    if additives:
        with st.expander(f"🧪 Detekovaná éčka a aditiva ({len(additives)})"):
            for a in additives:
                risk_emoji = "🟢" if a.get("risk") == "SAFE" else ("🟠" if a.get("risk") == "CAUTION" else "🔴")
                st.write(f"{risk_emoji} **{a.get('code')} - {a.get('name')}** ({a.get('purpose', '')})")
                if a.get("safetyNote"):
                    st.caption(a.get("safetyNote"))

    # Doporučení a alternativa
    st.markdown(f"💡 **Doporučení ke konzumaci:** {res.get('recommendation', '')}")
    if res.get("healthierAlternative"):
        st.markdown(f"🌱 **Zdravější alternativa:** {res.get('healthierAlternative', '')}")

    # Interaktivní dotazy na Gemini
    st.markdown("---")
    st.subheader("💬 Zeptejte se Gemini na toto složení")
    q_col1, q_col2, q_col3 = st.columns(3)
    quick_q = None
    with q_col1:
        if st.button("Obsahuje alergeny?"):
            quick_q = "Obsahuje tato potravina lepek, laktózu či jiné běžné alergeny?"
    with q_col2:
        if st.button("Je vhodná pro děti?"):
            quick_q = "Je toto složení vhodné pro malé děti?"
    with q_col3:
        if st.button("Hodí se při dietě?"):
            quick_q = "Hodí se tato potravina při redukční dietě na hubnutí?"

    user_q = st.text_input("Nebo napište vlastní dotaz:", value=quick_q if quick_q else "")
    if st.button("Odeslat dotaz"):
        if user_q:
            api_key = get_api_key()
            with st.spinner("Gemini odpovídá..."):
                answer = ask_followup_question(res, user_q, api_key)
                st.write(f"**Odpověď:** {answer}")
