# Fruits Warehouse — Android App

## इस संस्करण में क्या है
- Final Stock `.xlsx` upload (पहली worksheet)
- Lot, Customer, Product, Brand और Location के अनुसार खोज
- In / Out / Balance का शुरुआती रिकॉर्ड
- फोन में सेव Physical Count
- Difference report; `Approve & Adjust Balance` दबाने पर ही Balance बदलेगा
- Manual Inward / Outward entry
- CSV report export
- डेटा फोन में local save होता है; इस संस्करण में cloud sync या automatic photo-AI counting नहीं है।

## GitHub पर लगाने का आसान तरीका
1. GitHub में अपने Fruits Warehouse repository को खोलें।
2. अगर मौजूदा प्रोजेक्ट की जगह यह पूरा प्रोजेक्ट लगाना है, तो पहले पुरानी फाइलों की copy/backup रखें।
3. ZIP खोलकर सभी फाइलें repository के root में रखें। `MainActivity.kt` का path यह होना चाहिए:
   `app/src/main/java/com/example/fruitswarehouse/MainActivity.kt`
4. `.github/workflows/build.yml` भी repository में रखें।
5. Commit changes करें। फिर **Actions → Build Fruits Warehouse APK** खोलें।
6. Build green होने पर run के नीचे **Artifacts → Fruits-Warehouse-debug-apk** से APK लें।

## Stock Excel की अपेक्षित columns
`InwardDate, Lot No., Product Name, Brand, Count, In, Out, Balance, Location`

Customer headings जैसे `Customer Name : ABC FRUITS` भी पढ़ने की कोशिश की जाती है। XLSX की पहली worksheet पढ़ी जाती है। Import की गई Final Stock report मौजूदा data को replace करती है, इसलिए इसे सिर्फ final snapshot के लिए इस्तेमाल करें। उसी snapshot को दोबारा import करने से quantity double नहीं होती, लेकिन physical counts जो import से पहले दर्ज हों वे replace हो जाएँगे।

## महत्वपूर्ण सीमा
यह starter version है। Daily Inward/Outward transaction Excel import को आपके दोनों वास्तविक transaction sheets के column mapping के अनुसार जोड़ना बाकी है। अभी Movement tab से manual entry की जा सकती है। App का data इस फोन में रहता है; फोन बदलने पर स्वतः sync नहीं होगा।
