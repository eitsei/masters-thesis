import re
import time
import uuid
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse

# Configuration
START_URL = "https://www.nordea.fi/"
ALLOWED_DOMAIN = "www.nordea.fi"
MAX_PAGES = 50  # Limit to avoid running too long or overloading
CHUNK_SIZE = 1000  # Character count per chunk
CHUNK_OVERLAP = 100
OLLAMA_URL = "http://localhost:11434/api/embeddings"
EMBEDDING_MODEL = "nomic-embed-text"
QDRANT_URL = "http://localhost:6333/collections/banking-knowledge/points"

visited = set()
to_visit = [START_URL]

def get_embedding(text):
    try:
        response = requests.post(OLLAMA_URL, json={
            "model": EMBEDDING_MODEL,
            "prompt": text
        }, timeout=10)
        response.raise_for_status()
        return response.json()["embedding"]
    except Exception as e:
        print(f"Error getting embedding: {e}")
        return None

def insert_to_qdrant(points):
    try:
        response = requests.put(QDRANT_URL, json={"points": points}, timeout=10)
        response.raise_for_status()
        return True
    except Exception as e:
        print(f"Error inserting into Qdrant: {e}")
        return False

def clean_text(text):
    # Remove extra whitespaces
    text = re.sub(r'\s+', ' ', text)
    return text.strip()

def chunk_text(text, size=CHUNK_SIZE, overlap=CHUNK_OVERLAP):
    chunks = []
    start = 0
    while start < len(text):
        end = start + size
        chunks.append(text[start:end])
        start += size - overlap
    return chunks

def scrape_page(url):
    print(f"Scraping: {url}")
    try:
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        }
        res = requests.get(url, headers=headers, timeout=10)
        if res.status_code != 200:
            print(f"Failed to fetch {url} - Status: {res.status_code}")
            return []
        
        soup = BeautifulSoup(res.text, "html.parser")
        
        # Remove script, style, nav, footer, header elements
        for element in soup(["script", "style", "nav", "footer", "header", "iframe"]):
            element.decompose()
            
        title = soup.title.string if soup.title else url
        title = clean_text(title)
        
        # Get text from body or main container if it exists
        main_content = soup.find("main") or soup.find("article") or soup.body
        if not main_content:
            return []
            
        text = clean_text(main_content.get_text())
        if not text:
            return []
            
        # Extract links for crawling
        links = []
        for a in soup.find_all("a", href=True):
            href = a["href"]
            full_url = urljoin(url, href)
            parsed = urlparse(full_url)
            # Normalize url (remove query params and fragment)
            normalized = f"{parsed.scheme}://{parsed.netloc}{parsed.path}"
            if parsed.netloc == ALLOWED_DOMAIN and normalized not in visited:
                # Limit to specific language subdirectories or avoid large binary assets
                if not any(normalized.endswith(ext) for ext in ['.pdf', '.zip', '.png', '.jpg', '.jpeg', '.gif']):
                    links.append(normalized)
                    
        return {
            "title": title,
            "text": text,
            "links": links
        }
    except Exception as e:
        print(f"Error scraping {url}: {e}")
        return None

def main():
    print("Starting Web Scraper & Ingestor for Nordea.fi...")
    page_count = 0
    
    while to_visit and page_count < MAX_PAGES:
        url = to_visit.pop(0)
        if url in visited:
            continue
            
        visited.add(url)
        page_count += 1
        
        data = scrape_page(url)
        if not data:
            continue
            
        # Add new links to queue
        for link in data["links"]:
            if link not in visited and link not in to_visit:
                to_visit.append(link)
                
        # Chunk text
        chunks = chunk_text(data["text"])
        print(f"Split page into {len(chunks)} chunks.")
        
        points = []
        for i, chunk in enumerate(chunks):
            embedding = get_embedding(chunk)
            if not embedding:
                continue
                
            point_id = str(uuid.uuid4())
            payload = {
                "doc_content": chunk,
                "title": data["title"],
                "source": url,
                "filename": f"nordea_page_{page_count}_chunk_{i}.txt",
                "charset": "UTF-8"
            }
            points.append({
                "id": point_id,
                "vector": embedding,
                "payload": payload
            })
            
        if points:
            success = insert_to_qdrant(points)
            if success:
                print(f"Successfully inserted {len(points)} points from {url} into Qdrant.")
            else:
                print(f"Failed to insert points from {url}")
                
        # Delay to avoid overloading
        time.sleep(0.5)

    print(f"Completed! Scraped {page_count} pages.")

if __name__ == "__main__":
    main()
